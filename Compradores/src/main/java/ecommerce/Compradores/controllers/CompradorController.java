package ecommerce.Compradores.controllers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import ecommerce.Compradores.models.dtos.*;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import ecommerce.Compradores.models.CarritoDeCompra;
import ecommerce.Compradores.models.Compra;
import ecommerce.Compradores.models.Comprador;
import ecommerce.Compradores.models.Item;

import ecommerce.Compradores.proxys.TiendaProxy;
import ecommerce.Compradores.repositories.RepoCarritoDeCompra;
import ecommerce.Compradores.repositories.RepoCompra;
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
    RepoCompra repoCompra;

    @Autowired
    RepoCarritoDeCompra repoCarrito;

    @Autowired
    TiendaProxy proxy;

    @PostMapping("/comprador/newComprador")
    public @ResponseBody ResponseEntity<Object> crearComprador(@RequestBody DTOComprador comprador) {
        Comprador newComprador = new Comprador(comprador.getNombre(), comprador.getApellido());

        if (newComprador.getNombre().isEmpty() || newComprador.getApellido().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Faltan datos del comprador");
        }
        repoComprador.save(newComprador);

        return ResponseEntity.status(HttpStatus.CREATED).body("Comprador Creado, id: " + newComprador.getId());
    }

    @Transactional
    @PostMapping("/comprador/{compradorId}/items")
    public @ResponseBody ResponseEntity<Object> cargarItem(
            @PathVariable("compradorId") Long compradorId,
            @RequestBody DTOItem item) {

        Optional<Comprador> compradorOptional = repoComprador.findById(compradorId);

        if (!compradorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontro el comprador");
        } else {

            Comprador comprador = compradorOptional.get();

            DTORtaPublicacion res = proxy.publicacion(item.getTiendaId(), item.getPublicacionId());

            if (res.getStatus().equals("existe")) {
                //Item newItem = new Item(item.getCantidad(), item.getPublicacionId(), item.getTiendaId(), res.getPrecio(), comprador.getCarrito());

                if (comprador.getCarrito().getTiendaId() == null) {

                    comprador.getCarrito().setTiendaId(item.getTiendaId());

                    Item newItem = new Item(item.getCantidad(), item.getPublicacionId(), res.getPrecio(), comprador.getCarrito());

                    repoItem.save(newItem);

                    return ResponseEntity.status(HttpStatus.OK).body("Item agregado al carrito, " + newItem.getId());

                }

                if (comprador.getCarrito().getTiendaId() == item.getTiendaId()) {

                    Item newItem = new Item(item.getCantidad(), item.getPublicacionId(), res.getPrecio(), comprador.getCarrito());

                    repoItem.save(newItem);

                    return ResponseEntity.status(HttpStatus.OK).body("Item agregado al carrito, " + newItem.getId());


                } else {

                    return ResponseEntity.status(HttpStatus.CONFLICT).body("No se pueden agregar items de tiendas distintas");
                }

            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res.getStatus());
            }

        }
    }

    @Retry(name = "default", fallbackMethod = "noDisponible")
    @PostMapping("/comprador/{compradorId}/compras")
    public @ResponseBody ResponseEntity<Object> generarCompra(
            @PathVariable("compradorId") Long compradorId,
            @RequestBody DTOCompra compraReq
    ) {

        Optional<Comprador> compradorOptional = repoComprador.findById(compradorId);

        if (!compradorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontro el comprador");
        } else {

            Comprador comprador = compradorOptional.get();

            if (comprador.getCarrito().getTiendaId() == null) {

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No hay items en el carrito de compra para generar una compra");

            } else {

                Optional<CarritoDeCompra> carritoOptional = repoCarrito.findById(comprador.getCarrito().getId());
                CarritoDeCompra carrito = carritoOptional.get();

                ArrayList<Item> items = (ArrayList<Item>) repoItem.findByCarrito(carrito);

                List<Long> listaPublicacionesIds = new ArrayList<>();

                for (int i = 0; i < items.size(); i++) {
                    if (!listaPublicacionesIds.contains(items.get(i))) {
                        listaPublicacionesIds.add(items.get(i).getPublicacionId());
                    }
                }

                DTODatosVenta datosVenta = proxy.obtenerDatosVenta(carrito.getTiendaId(), listaPublicacionesIds);
//        		List<String> lista = new ArrayList<>();
//        		lista.add("efectivo");
//        		lista.add("debito");

//        		DTODatosVenta datosVenta = new DTODatosVenta("Thiago Almada", "7 dias");
                Iterator<Item> iteradorItems = items.iterator();

                Compra compra = new Compra(compradorId, comprador.getNombre() + " " + comprador.getApellido(), comprador.getCarrito().getTiendaId(), compraReq.getMetodoPago());

//				compra.getNombreCompletoVendedor(datosVenta.getNombreCompletoVendedor());

                repoCompra.save(compra);

                while (iteradorItems.hasNext()) {
                    Item item = iteradorItems.next();

                    item.setCarrito(null);
                    item.setCompra(compra);
                }

                carrito.setTiendaId(null);
                repoCarrito.save(carrito);

                repoItem.saveAll(items);

                return ResponseEntity.status(HttpStatus.OK).body("Compra Realizada, id: " + compra.getId());
            }
        }
    }

    public @ResponseBody ResponseEntity<Object> noDisponible(Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Estamos en mantenimiento, por favor intente mas tarde");
    }

    public @ResponseBody ResponseEntity<Object> noDisponible(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Estamos en mantenimiento, por favor intente mas tarde");
    }
}
