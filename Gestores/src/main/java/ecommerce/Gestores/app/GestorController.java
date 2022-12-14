package ecommerce.Gestores.app;
import ecommerce.Gestores.models.dtos.DTOGestor;
import ecommerce.Gestores.models.dtos.DTOProductoBase;
import ecommerce.Gestores.models.Gestor;
import ecommerce.Gestores.models.ProductoBase;

import ecommerce.Gestores.models.dtos.DTORtaGestor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RepositoryRestController
public class GestorController {
    @Autowired
    RepoGestor repoGestor;

    @Autowired
    RepoProductoBase repoProductoBase;

    @PostMapping("/gestores/newgestor")
    public @ResponseBody ResponseEntity<Object> crearGestor(@RequestBody DTOGestor gestor) {
        Gestor newGestor = new Gestor(gestor.getNombre(), gestor.getApellido());
        if (newGestor.getNombre().isEmpty() || newGestor.getApellido().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Faltan datos del gestor");
        }
        repoGestor.save(newGestor);
        return ResponseEntity.status(HttpStatus.CREATED).body("Gestor Creado con el id: " + newGestor.getId());
    }

    @PostMapping("/gestores/{gestorId}/productos")
    public @ResponseBody ResponseEntity<Object> crearProductoBase(
            @PathVariable("gestorId") Long gestorId,
            @RequestBody ArrayList<DTOProductoBase> productos
    ) {
    	List<Long> ids = new ArrayList<>();
        ArrayList<String> productosErroneos = new ArrayList<>(productos.size());
        Boolean todosOk = true;
        String finalMessage = "Estos productos ya existen o tienen un error[";
        Optional<Gestor> gestorOptional = repoGestor.findById(gestorId);


        if (!gestorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No existe el gestor");
        } else {
            Gestor gestor = gestorOptional.get();

            for (int i = 0; i < productos.size(); i++) {
                DTOProductoBase newProdBase = (DTOProductoBase) productos.get(i);

                if(newProdBase.getNombre().isEmpty() || newProdBase.getPrecio().toString().isEmpty() || newProdBase.getTiempoDeFabricacionEnDias() == null){
                    todosOk = false;
                    if (newProdBase.getNombre().isEmpty()) {
                        productosErroneos.add("Indice arr: " + i + ",");
                    } else {
                        productosErroneos.add(newProdBase.getNombre());
                    }
                } else {
                    ProductoBase unProducto = repoProductoBase.findByNombreAndGestorId(newProdBase.getNombre(), gestorId);

                    if (!(unProducto == null) && unProducto.getNombre() != null && unProducto.getGestor().getId() == gestorId) {
                        todosOk = false;
                        productosErroneos.add(unProducto.getNombre() + ",");
                    }
                }
            }

            if (!todosOk) {
                for (int i = 0; i < productosErroneos.size(); i++) {
                    finalMessage += productosErroneos.get(i) + " ";
                }
                finalMessage += "]";
                return ResponseEntity.status(HttpStatus.CONFLICT).body(finalMessage);
            }

            for (int i = 0; i < productos.size(); i++) {
                DTOProductoBase newProdBase = (DTOProductoBase) productos.get(i);
                ProductoBase productoBase = new ProductoBase(newProdBase.getNombre(),  newProdBase.getPrecio(), newProdBase.getDescripcion() , newProdBase.getTiempoDeFabricacionEnDias(), gestor);
                repoProductoBase.save(productoBase);
                ids.add(productoBase.getId());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body("Productos base creados, ids: " + ids.toString());
        }
    }

    @Transactional
    @PatchMapping("/gestores/{gestorId}/producto/{productoId}")
    public @ResponseBody ResponseEntity<Object> actualizarProductoBase(
            @PathVariable("gestorId") Long gestorId,
            @PathVariable("productoId") Long productoId,
            @RequestBody DTOProductoBase actualizacion
    ) {
        Optional<Gestor> gestorOptional = repoGestor.findById(gestorId);

        if (!gestorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontro el gestor");
        }
        Gestor gestor = gestorOptional.get();

        Optional<ProductoBase> productoBaseOptional = repoProductoBase.findById(productoId);

        if (!productoBaseOptional.isPresent() || !productoBaseOptional.get().isActivo()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("producto no encontrado");
        }

        ProductoBase unProducto = productoBaseOptional.get();
        unProducto.setNombre(actualizacion.getNombre());
        unProducto.setDescripcion(actualizacion.getDescripcion());
        unProducto.setPrecio(actualizacion.getPrecio());
        unProducto.setTiempoDeFabricacion(actualizacion.getTiempoDeFabricacionEnDias());

        return ResponseEntity.status(HttpStatus.OK).body("El producto " + unProducto.getNombre() + " fue actualizado, id: " + unProducto.getId());

    }

    @Transactional
    @DeleteMapping("/gestores/{gestorId}/producto/{productoId}")
    public @ResponseBody ResponseEntity<Object> deleteProductoBase(@PathVariable("gestorId") Long gestorId, @PathVariable("productoId") Long productoId) {
        Optional<Gestor> gestorOptional = repoGestor.findById(gestorId);
        Optional<ProductoBase> productoBaseOptional = repoProductoBase.findById(productoId);

        if (gestorOptional.isPresent() && productoBaseOptional.isPresent()) {
            ProductoBase productoBase = productoBaseOptional.get();
            if (productoBase.isActivo()) {
                productoBase.setActivo(false);
                return ResponseEntity.status(HttpStatus.OK).body("Producto base " + productoBase.getNombre() + " fue borrado");
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontro el producto base");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontro el gestor");
    }

    @PostMapping("/gestores/productos")
    public DTORtaGestor buscarTiempoDeFabricacion(@RequestBody List<Long>productosBaseIds) {

    	Integer mayorTiempo = 0;

        for (int i = 0; i < productosBaseIds.size(); i++) {
            Long idProd = productosBaseIds.get(i);
            Optional<ProductoBase> productoBaseOptional = repoProductoBase.findById(idProd);
            if(productoBaseOptional.isPresent()){
                ProductoBase productoBase = productoBaseOptional.get();
                if (productoBase.getTiempoDeFabricacion() > mayorTiempo){
                    mayorTiempo = productoBase.getTiempoDeFabricacion();
                }
            }
        }

        System.out.println("Major tiempo:" + mayorTiempo);
    	return new DTORtaGestor(mayorTiempo);
    }

}
