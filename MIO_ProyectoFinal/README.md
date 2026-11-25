# Analizadores del lenguaje MIO

Proyecto final - Teoria de la Computacion.

## Requisitos

Java JDK 8 o posterior (`javac` y `java` disponibles en la terminal).

## Uso (Windows, desde la carpeta del proyecto)

```bat
analex.bat ejemplos\factorial.mio
anasin.bat ejemplos\factorial.lex
```

## Uso (Linux/macOS)

```sh
./analex ejemplos/factorial.mio
./anasin ejemplos/factorial.lex
```

## Compilacion manual

```sh
javac -encoding UTF-8 Analex.java Anasin.java
java Analex ejemplos/factorial.mio
java Anasin ejemplos/factorial.lex
```

El analizador lexico produce `.lex` (un tipo de token por linea) y `.sim` (IDS, TXT, VAL). Si encuentra errores lexicos, muestra la linea y no genera esos archivos. El analizador sintactico muestra `Compilacion exitosa` o la descripcion del error, con el numero de token y, cuando tiene el `.mio` original, la linea de codigo.

Los comentarios con `#` al inicio de la linea se validan y omiten del `.lex`, igual que en el ejemplo del proyecto. Las literales de texto admiten comillas rectas y comillas tipograficas del ejemplo. No se ejecutan las operaciones del codigo MIO: el objetivo es analizarlo, no interpretarlo.


