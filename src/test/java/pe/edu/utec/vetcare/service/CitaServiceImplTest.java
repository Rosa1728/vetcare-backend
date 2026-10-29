package pe.edu.utec.vetcare.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.vetcare.dto.request.CreateCitaRequest;
import pe.edu.utec.vetcare.entity.MascotaEntity;
import pe.edu.utec.vetcare.enums.EstadoCita;
import pe.edu.utec.vetcare.entity.VeterinarioEntity;
import pe.edu.utec.vetcare.exception.CitaHorarioOcupadoException;
import pe.edu.utec.vetcare.mapper.CitaMapper;
import pe.edu.utec.vetcare.repository.CitaRepository;
import pe.edu.utec.vetcare.repository.MascotaRepository;
import pe.edu.utec.vetcare.repository.VeterinarioRepository;
import pe.edu.utec.vetcare.service.impl.CitaServiceImpl;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitaServiceImplTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private VeterinarioRepository veterinarioRepository;

    @Mock
    private MascotaRepository mascotaRepository;

    @Mock
    private CitaMapper citaMapper;

    @Mock SeguimientoCitaService seguimiento;
    @Mock HorarioReservaCitas reservas;
    @Mock ReglasHorarioAtencion reloj;
    private CitaServiceImpl citaService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@test.local", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        citaService =
                new CitaServiceImpl(
                        citaRepository,
                        veterinarioRepository,
                        mascotaRepository,
                        citaMapper, seguimiento, reservas, reloj
                );
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void debeLanzarErrorCuandoVeterinarioTieneHorarioOcupado() {

        CreateCitaRequest request =
                new CreateCitaRequest();

        request.setIdVeterinario(1L);
        request.setIdMascota(1L);

        request.setFechaCita(
                LocalDateTime.now()
                        .plusDays(1)
        );

        request.setMotivoCita(
                "Control de mascota"
        );

        VeterinarioEntity veterinario =
                new VeterinarioEntity();

        MascotaEntity mascota =
                new MascotaEntity();

        when(
                veterinarioRepository
                        .findById(1L)
        ).thenReturn(
                Optional.of(veterinario)
        );

        when(
                mascotaRepository
                        .findById(1L)
        ).thenReturn(
                Optional.of(mascota)
        );

        when(
                citaRepository
                        .existsCitaSuperpuesta(
                                eq(1L),
                                any(LocalDateTime.class),
                                any(LocalDateTime.class),
                                eq(EstadoCita.CANCELADA)
                        )
        ).thenReturn(true);

        assertThrows(
                CitaHorarioOcupadoException.class,
                () ->
                        citaService.create(request)
        );
    }

    @Test
    void debeLanzarErrorCuandoVeterinarioNoExiste() {

        CreateCitaRequest request =
                new CreateCitaRequest();

        request.setIdVeterinario(99L);
        request.setIdMascota(1L);

        request.setFechaCita(
                LocalDateTime.now()
                        .plusDays(1)
        );

        request.setMotivoCita(
                "Consulta"
        );

        when(
                veterinarioRepository
                        .findById(99L)
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                RuntimeException.class,
                () ->
                        citaService.create(request)
        );
    }
}