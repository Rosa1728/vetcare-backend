package pe.edu.utec.vetcare.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.utec.vetcare.validation.PoliticaContrasena;
import static org.junit.jupiter.api.Assertions.*;
class PoliticaContrasenaTest {
 @ParameterizedTest @ValueSource(strings={"Corta1!","solo123!","SOLO123!","SinNumero!","SinSimbolo123","Password1!","Contraseña1!","VetCare2026!"})
 void rechazaInsegurasYComunes(String value){assertFalse(PoliticaContrasena.segura(value));}
 @ParameterizedTest @ValueSource(strings={"Aq1!bcde","Áñ2!bcde","Consulta@Segura37"})
 void admiteOchoCaracteresYUnicode(String value){assertTrue(PoliticaContrasena.segura(value));}
 @Test void limitaBytesDeBcryptSinTruncar(){assertTrue(PoliticaContrasena.segura("Aa1!"+"é".repeat(34)));assertFalse(PoliticaContrasena.segura("Aa1!"+"é".repeat(35)));}
}
