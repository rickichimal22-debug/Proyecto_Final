import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Analizador lexico del lenguaje MIO. */
public class Analex {
    private static final Set<String> RESERVADAS = new HashSet<String>(Arrays.asList(
        "PROGRAMA", "FINPROG", "SI", "ENTONCES", "SINO", "FINSI",
        "REPITE", "VECES", "FINREP", "IMPRIME", "LEE"
    ));

    static class Token {
        final String tipo;
        final String texto;
        final int linea;

        Token(String tipo, String texto, int linea) {
            this.tipo = tipo;
            this.texto = texto;
            this.linea = linea;
        }
    }

    static class Resultado {
        final List<Token> tokens = new ArrayList<Token>();
        final List<String> errores = new ArrayList<String>();
        final Map<String, String> ids = new LinkedHashMap<String, String>();
        final Map<String, String> textos = new LinkedHashMap<String, String>();
        final Map<String, BigInteger> valores = new LinkedHashMap<String, BigInteger>();

        void agregar(String tipo, String texto, int linea) {
            tokens.add(new Token(tipo, texto, linea));
            if ("[id]".equals(tipo) && !ids.containsKey(texto)) {
                ids.put(texto, String.format(Locale.ROOT, "ID%02d", ids.size() + 1));
            } else if ("[txt]".equals(tipo) && !textos.containsKey(texto)) {
                textos.put(texto, String.format(Locale.ROOT, "TX%02d", textos.size() + 1));
            } else if ("[val]".equals(tipo) && !valores.containsKey(texto)) {
                valores.put(texto, new BigInteger(texto.substring(2), 16));
            }
        }

        void error(int linea, String descripcion) {
            errores.add("Error lexico en linea " + linea + ": " + descripcion);
        }
    }

    // Los simbolos y los espacios separan las unidades lexicas.
    private static boolean separador(char c) {
        return Character.isWhitespace(c) || "=<>+-*/\"“”#".indexOf(c) >= 0;
    }

    static Resultado analizar(Path ruta) throws IOException {
        Resultado resultado = new Resultado();
        List<String> lineas = Files.readAllLines(ruta, StandardCharsets.UTF_8);
        for (int numero = 0; numero < lineas.size(); numero++) {
            String linea = lineas.get(numero);
            int n = numero + 1;
            int i = 0;
            while (i < linea.length() && Character.isWhitespace(linea.charAt(i))) i++;

            // Un comentario ocupa la linea y no se incluye en .lex,
            // como en el ejemplo de factorial del enunciado.
            if (i < linea.length() && linea.charAt(i) == '#') {
                if (linea.length() - i - 1 > 100) {
                    resultado.error(n, "el comentario supera los 100 caracteres");
                }
                continue;
            }

            while (i < linea.length()) {
                char c = linea.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }

                if (c == '"' || c == '“') {
                    char cierre = c == '“' ? '”' : '"';
                    int inicio = i++;
                    while (i < linea.length() && linea.charAt(i) != cierre) i++;
                    if (i == linea.length()) {
                        resultado.error(n, "texto sin comillas de cierre");
                        break;
                    }
                    i++;
                    resultado.agregar("[txt]", linea.substring(inicio, i), n);
                    continue;
                }

                if (c == '=') {
                    if (i + 1 < linea.length() && linea.charAt(i + 1) == '=') {
                        resultado.agregar("[op_rel]", "==", n);
                        i += 2;
                    } else {
                        resultado.agregar("=", "=", n);
                        i++;
                    }
                    continue;
                }
                if (c == '>' || c == '<') {
                    resultado.agregar("[op_rel]", String.valueOf(c), n);
                    i++;
                    continue;
                }
                if ("+-*/".indexOf(c) >= 0) {
                    resultado.agregar("[op_ar]", String.valueOf(c), n);
                    i++;
                    continue;
                }
                if (c == '#') {
                    resultado.error(n, "el comentario debe comenzar al inicio de la linea");
                    break;
                }
                if (c == '”') {
                    resultado.error(n, "comillas de cierre sin apertura");
                    i++;
                    continue;
                }

                int inicio = i;
                while (i < linea.length() && !separador(linea.charAt(i))) i++;
                String palabra = linea.substring(inicio, i);
                if (RESERVADAS.contains(palabra)) {
                    resultado.agregar(palabra, palabra, n);
                } else if (palabra.matches("0x[0-9A-F]+")) {
                    resultado.agregar("[val]", palabra, n);
                } else if (palabra.matches("[A-Za-z][A-Za-z0-9]*")) {
                    if (palabra.length() > 16) {
                        resultado.error(n, "identificador demasiado largo (maximo 16): " + palabra);
                    } else {
                        resultado.agregar("[id]", palabra, n);
                    }
                } else {
                    resultado.error(n, "unidad lexica no valida: " + palabra);
                }
            }
        }
        return resultado;
    }

    private static Path rutaDeSalida(Path origen, String extension) {
        String nombre = origen.getFileName().toString();
        int indice = nombre.lastIndexOf('.');
        if (indice >= 0) nombre = nombre.substring(0, indice);
        return origen.resolveSibling(nombre + extension);
    }

    private static void escribirArchivos(Path origen, Resultado r) throws IOException {
        List<String> lineasLex = new ArrayList<String>();
        for (Token token : r.tokens) lineasLex.add(token.tipo);
        Files.write(rutaDeSalida(origen, ".lex"), lineasLex, StandardCharsets.UTF_8);

        List<String> lineasSim = new ArrayList<String>();
        lineasSim.add("IDS");
        for (Map.Entry<String, String> e : r.ids.entrySet()) {
            lineasSim.add(e.getKey() + ", " + e.getValue());
        }
        lineasSim.add("");
        lineasSim.add("TXT");
        for (Map.Entry<String, String> e : r.textos.entrySet()) {
            lineasSim.add(e.getKey() + ", " + e.getValue());
        }
        lineasSim.add("");
        lineasSim.add("VAL");
        for (Map.Entry<String, BigInteger> e : r.valores.entrySet()) {
            lineasSim.add(e.getKey() + ", " + e.getValue().toString());
        }
        Files.write(rutaDeSalida(origen, ".sim"), lineasSim, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: analex archivo.mio");
            System.exit(1);
        }
        Path origen = Paths.get(args[0]);
        if (!origen.getFileName().toString().endsWith(".mio")) {
            System.out.println("Error: se requiere un archivo con extension .mio");
            System.exit(1);
        }
        try {
            Resultado r = analizar(origen);
            if (!r.errores.isEmpty()) {
                // Evita conservar salidas anteriores cuando el archivo tiene errores.
                Files.deleteIfExists(rutaDeSalida(origen, ".lex"));
                Files.deleteIfExists(rutaDeSalida(origen, ".sim"));
                for (String error : r.errores) System.out.println(error);
                System.exit(1);
            }
            escribirArchivos(origen, r);
            System.out.println("Analisis lexico exitoso.");
            System.out.println("Generados: " + rutaDeSalida(origen, ".lex"));
            System.out.println("           " + rutaDeSalida(origen, ".sim"));
        } catch (IOException e) {
            System.out.println("Error al leer o escribir el archivo: " + e.getMessage());
            System.exit(1);
        }
    }
}
