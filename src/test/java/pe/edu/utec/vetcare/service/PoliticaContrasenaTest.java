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
 @Test void explicaSoloLosRequisitosIncumplidos(){
  assertEquals("Incluye una letra mayúscula.",PoliticaContrasena.error("segura!123"));
  assertEquals("Agrega al menos un símbolo.",PoliticaContrasena.error("Segura123"));
  assertEquals("Agrega al menos un número.",PoliticaContrasena.error("Segura!xyz"));
  String comun=PoliticaContrasena.error("VetCare2026!");
  assertEquals("Elige una contraseña menos común o difícil de adivinar.",comun);assertFalse(comun.contains("VetCare"));assertTrue(comun.contains("menos común"));assertFalse(comun.contains("8 caracteres"));assertFalse(comun.contains("bytes"));
  assertEquals("La contraseña es demasiado larga. Usa una más breve.",PoliticaContrasena.error("Aa1!"+"é".repeat(35)));
  assertTrue(PoliticaContrasena.segura("Aa1!"+"😀".repeat(17)));
  assertFalse(PoliticaContrasena.segura("Aa1!"+"😀".repeat(18)));
  var ex=assertThrows(pe.edu.utec.vetcare.exception.BadRequestException.class,()->PoliticaContrasena.validar("Segura123"));
  assertEquals(PoliticaContrasena.error("Segura123"),ex.getMessage());
 }
 record Clave(@pe.edu.utec.vetcare.validation.ContrasenaSegura String contrasena){}
 @Test void validacionDeDtoUsaElMismoMensajeSinBaseDeDatos(){
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
   var validator=factory.getValidator();
   for(String clave:java.util.List.of("segura!123","VetCare2026!","Aa1!"+"é".repeat(35))){
    var violations=validator.validate(new Clave(clave));assertEquals(1,violations.size());
    assertEquals(PoliticaContrasena.error(clave),violations.iterator().next().getMessage());
   }
   assertTrue(validator.validate(new Clave("Consulta@Segura37")).isEmpty());
  }
 }
}
