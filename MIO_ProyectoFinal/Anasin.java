import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Analizador sintactico descendente recursivo para la gramatica MIO. */
public class Anasin {
    private final List<String> tokens;
    private final List<Integer> lineasOrigen;
    private int posicion = 0;

    static class ErrorSintactico extends Exception {
        final int posicion;
        ErrorSintactico(String mensaje, int posicion) {
            super(mensaje);
            this.posicion = posicion;
        }
    }

    private Anasin(List<String> tokens, List<Integer> lineasOrigen) {
        this.tokens = tokens;
        this.lineasOrigen = lineasOrigen;
    }

    private String actual() {
        return posicion < tokens.size() ? tokens.get(posicion) : "FIN DE ARCHIVO";
    }

    private boolean es(String tipo) {
        return actual().equals(tipo);
    }

    private ErrorSintactico error(String contexto, String detalle) {
        return new ErrorSintactico("sentencia " + contexto + ": " + detalle
                + " (se encontro " + actual() + ")", posicion);
    }

    private void exigir(String tipo, String contexto) throws ErrorSintactico {
        if (!es(tipo)) throw error(contexto, "se esperaba " + tipo);
        posicion++;
    }

    private void elemento(String contexto) throws ErrorSintactico {
        if (es("[id]") || es("[val]")) {
            posicion++;
        } else {
            throw error(contexto, "se esperaba un identificador o valor hexadecimal");
        }
    }

    private void comparacion() throws ErrorSintactico {
        exigir("[id]", "SI");
        exigir("[op_rel]", "SI");
        elemento("SI");
    }

    private void sentencia() throws ErrorSintactico {
        if (es("[id]")) {
            posicion++;
            exigir("=", "asignacion");
            elemento("asignacion");
            if (es("[op_ar]")) {
                posicion++;
                elemento("asignacion aritmetica");
            }
        } else if (es("SI")) {
            posicion++;
            comparacion();
            exigir("ENTONCES", "SI");
            sentencias("SI", "SINO", "FINSI");
            if (es("SINO")) {
                posicion++;
                sentencias("SINO", "FINSI");
            }
            exigir("FINSI", "SI");
        } else if (es("REPITE")) {
            posicion++;
            elemento("REPITE");
            exigir("VECES", "REPITE");
            sentencias("REPITE", "FINREP");
            exigir("FINREP", "REPITE");
        } else if (es("IMPRIME")) {
            posicion++;
            if (es("[txt]")) posicion++;
            else elemento("IMPRIME");
        } else if (es("LEE")) {
            posicion++;
            exigir("[id]", "LEE");
        } else if (es("#")) {
            // Compatible con la regla de comentario en un .lex escrito a mano.
            // Analex omite los comentarios, como en el ejemplo del PDF.
            posicion++;
            exigir("[comentario]", "comentario");
        } else {
            throw error("general", "inicio de sentencia no valido");
        }
    }

    private void sentencias(String contexto, String... fin) throws ErrorSintactico {
        Set<String> cierres = new HashSet<String>(Arrays.asList(fin));
        if (cierres.contains(actual()) || es("FIN DE ARCHIVO")) {
            throw error(contexto, "se requiere al menos una sentencia en el bloque");
        }
        while (!cierres.contains(actual())) {
            if (es("FIN DE ARCHIVO") || es("FINPROG") || es("FINREP")
                    || es("FINSI") || es("SINO")) {
                throw error(contexto, "falta el cierre correcto del bloque (" + Arrays.toString(fin) + ")");
            }
            sentencia();
        }
    }

    private void programa() throws ErrorSintactico {
        exigir("PROGRAMA", "PROGRAMA");
        exigir("[id]", "PROGRAMA");
        sentencias("PROGRAMA", "FINPROG");
        exigir("FINPROG", "PROGRAMA");
        if (posicion != tokens.size()) {
            throw error("PROGRAMA", "hay tokens despues de FINPROG");
        }
    }

    private String ubicacion(int indice) {
        String lugar = "token " + (indice + 1) + " del archivo .lex";
        if (indice < lineasOrigen.size() && lineasOrigen.get(indice) > 0) {
            lugar += ", linea " + lineasOrigen.get(indice) + " del archivo .mio";
        }
        return lugar;
    }

    /** Recupera las lineas originales solo si el .mio coincide con el .lex. */
    private static List<Integer> recuperarLineas(Path archivoLex, List<String> tokens) {
        List<Integer> lineas = new ArrayList<Integer>();
        String nombre = archivoLex.getFileName().toString();
        Path mio = archivoLex.resolveSibling(nombre.substring(0, nombre.length() - 4) + ".mio");
        if (!Files.isRegularFile(mio)) return lineas;
        try {
            Analex.Resultado r = Analex.analizar(mio);
            if (!r.errores.isEmpty() || r.tokens.size() != tokens.size()) return lineas;
            for (int i = 0; i < tokens.size(); i++) {
                if (!tokens.get(i).equals(r.tokens.get(i).tipo)) return new ArrayList<Integer>();
                lineas.add(r.tokens.get(i).linea);
            }
        } catch (IOException e) {
            return new ArrayList<Integer>();
        }
        return lineas;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: anasin archivo.lex");
            System.exit(1);
        }
        Path ruta = Paths.get(args[0]);
        if (!ruta.getFileName().toString().endsWith(".lex")) {
            System.out.println("Error: se requiere un archivo con extension .lex");
            System.exit(1);
        }
        try {
            List<String> tokens = new ArrayList<String>();
            for (String linea : Files.readAllLines(ruta, StandardCharsets.UTF_8)) {
                if (!linea.trim().isEmpty()) tokens.add(linea.trim());
            }
            Anasin analizador = new Anasin(tokens, recuperarLineas(ruta, tokens));
            try {
                analizador.programa();
                System.out.println("Compilacion exitosa");
            } catch (ErrorSintactico e) {
                System.out.println("Error: " + e.getMessage() + " [" + analizador.ubicacion(e.posicion) + "]");
                System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("Error al leer archivo: " + e.getMessage());
            System.exit(1);
        }
    }
}
