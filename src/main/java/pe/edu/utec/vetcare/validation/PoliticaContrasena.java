package pe.edu.utec.vetcare.validation;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
public final class PoliticaContrasena {
 private PoliticaContrasena(){}
 public static List<String> errores(String value){
  String s=value==null?"":value;
  List<String> errores=new ArrayList<>();
  if(s.codePointCount(0,s.length())<8)errores.add("Usa al menos 8 caracteres.");
  // BCrypt no debe recibir entradas que puedan truncarse o exceder su límite UTF-8.
  if(s.getBytes(StandardCharsets.UTF_8).length>72)errores.add("La contraseña es demasiado larga. Usa una más breve.");
  if(!s.codePoints().anyMatch(Character::isUpperCase))errores.add("Incluye una letra mayúscula.");
  if(!s.codePoints().anyMatch(Character::isLowerCase))errores.add("Incluye una letra minúscula.");
  if(!s.codePoints().anyMatch(Character::isDigit))errores.add("Agrega al menos un número.");
  if(!s.codePoints().anyMatch(c->!Character.isLetterOrDigit(c)&&!Character.isWhitespace(c)))errores.add("Agrega al menos un símbolo.");
  String base=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");
  if(Set.of("password","contrasena","qwerty","admin","administrator","letmein","welcome","bienvenido","vetcare","abc").contains(base))errores.add("Elige una contraseña menos común o difícil de adivinar.");
  return List.copyOf(errores);
 }
 public static String error(String s){return String.join(" ",errores(s));}
 public static boolean segura(String s){return errores(s).isEmpty();}
 public static void validar(String s){String error=error(s);if(!error.isEmpty())throw new pe.edu.utec.vetcare.exception.BadRequestException(error);}
}
