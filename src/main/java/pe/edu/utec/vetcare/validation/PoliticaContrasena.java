package pe.edu.utec.vetcare.validation;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
public final class PoliticaContrasena {
 private PoliticaContrasena(){}
 public static boolean segura(String s){
  if(s==null||s.codePointCount(0,s.length())<8||s.getBytes(StandardCharsets.UTF_8).length>72)return false;
  if(!s.codePoints().anyMatch(Character::isUpperCase)||!s.codePoints().anyMatch(Character::isLowerCase)||!s.codePoints().anyMatch(Character::isDigit)||!s.codePoints().anyMatch(c->!Character.isLetterOrDigit(c)&&!Character.isWhitespace(c)))return false;
  String base=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");
  return !Set.of("password","contrasena","qwerty","admin","administrator","letmein","welcome","bienvenido","vetcare","abc").contains(base);
 }
 public static void validar(String s){if(!segura(s))throw new pe.edu.utec.vetcare.exception.BadRequestException("Usa al menos 8 caracteres, mayúscula, minúscula, número y símbolo; evita contraseñas comunes (máximo 72 bytes)");}
}
