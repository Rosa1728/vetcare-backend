package pe.edu.utec.vetcare.controller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import java.util.List;
import pe.edu.utec.vetcare.service.*;
import pe.edu.utec.vetcare.dto.request.*;
import pe.edu.utec.vetcare.dto.response.*;
@RestController @RequestMapping("/api/citas") @RequiredArgsConstructor
public class SeguimientoCitaController {
 private final SeguimientoCitaService seguimiento;
 private final CitaService citas;
 @PostMapping("/{id}/iniciar-atencion") @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO')")
 public CitaResponse iniciar(@PathVariable Long id){seguimiento.iniciar(id);return citas.getById(id);}
 @PutMapping("/{id}/no-asistio") @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO')")
 public CitaResponse noAsistio(@PathVariable Long id){seguimiento.noAsistio(id);return citas.getById(id);}
 @PostMapping("/{id}/regularizaciones") @PreAuthorize("hasRole('VETERINARIO')")
 public RegularizacionResponse solicitar(@PathVariable Long id,@Valid @RequestBody SolicitarRegularizacionRequest request){return seguimiento.solicitar(id,request.getMotivo());}
 @GetMapping("/regularizaciones") @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO')")
 public List<RegularizacionResponse> listar(){return seguimiento.listar();}
 @PutMapping("/regularizaciones/{id}/resolver") @PreAuthorize("hasRole('ADMIN')")
 public RegularizacionResponse resolver(@PathVariable Long id,@Valid @RequestBody ResolverRegularizacionRequest request){return seguimiento.resolver(id,request.getAprobar());}
}
