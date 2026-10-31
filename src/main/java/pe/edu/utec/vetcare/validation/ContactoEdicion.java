package pe.edu.utec.vetcare.validation;

import pe.edu.utec.vetcare.exception.BadRequestException;

/** Mantiene las reglas de formato existentes y admite vacíos explícitos para quitar contacto. */
public final class ContactoEdicion {
    private ContactoEdicion() {}
    public static void validar(String celular,String direccion,boolean peruano) {
        if(celular==null&&direccion==null)throw new BadRequestException("Indica celular o dirección para actualizar.");
        if(celular!=null&&!celular.isEmpty()) {
            if(peruano&&!celular.matches("9[0-9]{8}"))throw new BadRequestException("Ingresa un celular válido de 9 dígitos que comience con 9.");
            if(celular.length()>20||!celular.matches("[+]?[0-9][0-9 ()-]{5,19}"))throw new BadRequestException("Ingresa un celular válido de 6 a 20 caracteres.");
        }
        if(direccion!=null&&direccion.length()>200)throw new BadRequestException("La dirección no puede superar 200 caracteres.");
    }
}
