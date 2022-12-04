package ecommerce.Compradores.controllers;

import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import ecommerce.Compradores.models.Comprador;
import ecommerce.Compradores.models.Item;
import ecommerce.Compradores.models.dtos.DTOComprador;
import ecommerce.Compradores.models.dtos.DTOItem;
import ecommerce.Compradores.models.dtos.DTORtaPublicacion;
import ecommerce.Compradores.proxys.TiendaProxy;
import ecommerce.Compradores.repositories.RepoComprador;
import ecommerce.Compradores.repositories.RepoItem;

@RepositoryRestController
@RestController
public class CompradorController {
	
	@Autowired
	RepoComprador repoComprador;
	
	@Autowired
	RepoItem repoItem;
	
	@Autowired
	TiendaProxy proxy;
	
	@PostMapping("/compradores/comprador")
	public @ResponseBody ResponseEntity<Object> crearComprador(@RequestBody DTOComprador comprador) {
		Comprador newComprador = new Comprador(comprador.getNombre(), comprador.getApellido());
		
		if(newComprador.getNombre().isEmpty() || newComprador.getApellido().isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Faltan datos del comprador");
		}
		repoComprador.save(newComprador);
		
		return ResponseEntity.status(HttpStatus.CREATED).body("Comprador Creado, id: " + newComprador.getId());
	}
	
	@Transactional
	@PostMapping("/compradores/{compradorId}/items")
	public @ResponseBody ResponseEntity<Object> cargarItem(
			@PathVariable("compradorId") Long compradorId,
			@RequestBody DTOItem item) {
		
		Optional<Comprador> compradorOptional = repoComprador.findById(compradorId);
		
		if (!compradorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontro el comprador");
        } else {
        	
        	Comprador comprador = compradorOptional.get();
        	
        	DTORtaPublicacion res = proxy.publicacion(item.getTiendaId(), item.getPublicacionId());
        	
        	if(res.getStatus().equals("existe")) {
        		Item newItem = new Item(item.getCantidad(), item.getPublicacionId(), res.getPrecio(), comprador.getCarrito());
            	
            	repoItem.save(newItem);
            	
            	return ResponseEntity.status(HttpStatus.OK).body("Item agregado al carrito, " + newItem.getId());
        		
        	} else {
        		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res.getStatus());
        	}      	
        	
        }		
	}

}