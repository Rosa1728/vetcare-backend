package pe.edu.utec.vetcare.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.utec.vetcare.dto.request.UpdateCitaRequest;
import pe.edu.utec.vetcare.entity.*;
import pe.edu.utec.vetcare.enums.EstadoCita;
import pe.edu.utec.vetcare.exception.EstadoCitaInvalidoException;
import pe.edu.utec.vetcare.mapper.CitaMapper;
import pe.edu.utec.vetcare.repository.*;
import pe.edu.utec.vetcare.service.impl.CitaServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitaReprogramacionTest {
    @Mock CitaRepository citas;
    @Mock VeterinarioRepository veterinarios;
    @Mock MascotaRepository mascotas;
    @Mock CitaMapper mapper;
    @Mock SeguimientoCitaService seguimiento;

    @AfterEach
    void limpiarContexto() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @CsvSource({
        "ADMIN,ATENDIDA", "ADMIN,CANCELADA", "ADMIN,NO_ASISTIO",
        "PROPIETARIO,ATENDIDA", "PROPIETARIO,CANCELADA", "PROPIETARIO,NO_ASISTIO",
        "VETERINARIO,ATENDIDA", "VETERINARIO,CANCELADA", "VETERINARIO,NO_ASISTIO"
    })
    void rechazaCitasCerradasSinModificarNiGuardar(String rol, EstadoCita estado) {
        autenticar(rol);
        CitaEntity cita = cita(estado);
        LocalDateTime fechaOriginal = cita.getFechaCita();
        when(citas.findByIdForUpdate(1L)).thenReturn(Optional.of(cita));

        assertThrows(EstadoCitaInvalidoException.class, () -> servicio().update(1L, solicitud()));

        assertEquals(fechaOriginal, cita.getFechaCita());
        assertEquals("Control original", cita.getMotivoCita());
        assertEquals(estado, cita.getEstado());
        verify(citas).findByIdForUpdate(1L);
        verifyNoMoreInteractions(citas);
        verifyNoInteractions(mapper, veterinarios, mascotas);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,PENDIENTE", "ADMIN,CONFIRMADA", "PROPIETARIO,PENDIENTE"})
    void conservaReprogramacionesPermitidas(String rol, EstadoCita estado) {
        autenticar(rol);
        CitaEntity cita = cita(estado);
        UpdateCitaRequest request = solicitud();
        when(citas.findByIdForUpdate(1L)).thenReturn(Optional.of(cita));
        when(citas.save(cita)).thenReturn(cita);

        servicio().update(1L, request);

        verify(citas).existsOtraCitaSuperpuesta(2L, request.getFechaCita().minusMinutes(30),
                request.getFechaCita().plusMinutes(30), EstadoCita.CANCELADA, 1L);
        verify(mapper).updateEntity(request, cita);
        verify(citas).save(cita);
        assertEquals(estado, cita.getEstado());
    }

    @ParameterizedTest
    @CsvSource({"PROPIETARIO,CONFIRMADA", "VETERINARIO,PENDIENTE", "VETERINARIO,CONFIRMADA"})
    void conservaRestriccionesDeRol(String rol, EstadoCita estado) {
        autenticar(rol);
        when(citas.findByIdForUpdate(1L)).thenReturn(Optional.of(cita(estado)));
        assertThrows(AccessDeniedException.class, () -> servicio().update(1L, solicitud()));
        verify(citas).findByIdForUpdate(1L);
        verifyNoMoreInteractions(citas);
        verifyNoInteractions(mapper);
    }

    private void autenticar(String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "propietario@test.local", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private CitaEntity cita(EstadoCita estado) {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setCorreo("propietario@test.local");
        PropietarioEntity propietario = new PropietarioEntity();
        propietario.setUsuario(usuario);
        MascotaEntity mascota = new MascotaEntity();
        mascota.setPropietario(propietario);
        VeterinarioEntity veterinario = new VeterinarioEntity();
        veterinario.setIdVeterinario(2L);
        CitaEntity cita = new CitaEntity();
        cita.setIdCita(1L);
        cita.setEstado(estado);
        cita.setMascota(mascota);
        cita.setVeterinario(veterinario);
        cita.setFechaCita(LocalDateTime.of(2030, 1, 1, 10, 0));
        cita.setMotivoCita("Control original");
        return cita;
    }

    private UpdateCitaRequest solicitud() {
        UpdateCitaRequest request = new UpdateCitaRequest();
        request.setFechaCita(LocalDateTime.now().plusDays(2));
        request.setMotivoCita("Control reprogramado");
        return request;
    }

    private CitaServiceImpl servicio() {
        return new CitaServiceImpl(citas, veterinarios, mascotas, mapper, seguimiento, org.mockito.Mockito.mock(HorarioReservaCitas.class), org.mockito.Mockito.mock(ReglasHorarioAtencion.class));
    }
}
