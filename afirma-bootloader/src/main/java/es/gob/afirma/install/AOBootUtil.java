/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

package es.gob.afirma.install;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** M&eacute;todos generales de utilidad para toda la aplicaci&oacute;n.
 * @version 0.3.1 */
final class AOBootUtil {

    /** Gestor de registro. */
    private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$;

    private AOBootUtil() {
        // No permitimos la instanciacion
    }

    /** Esquemas de ruta soportados. */
    private static final String[] SUPPORTED_URI_SCHEMES = new String[] {
        "http", //$NON-NLS-1$
        "https", //$NON-NLS-1$
        "file" //$NON-NLS-1$
    };

    /** Crea una URI a partir de un nombre de fichero local o una URL.
     * @param file Nombre del fichero local o URL
     * @return URI (<code>file://</code>) del fichero local o URL
     * @throws URISyntaxException cuando ocurre cualquier problema creando la URI */
    static URI createURI(final String file) throws URISyntaxException {

        if (file == null) {
            throw new IllegalArgumentException("No se puede crear una URI a partir de un nulo"); //$NON-NLS-1$
        }

        // Cambiamos los caracteres Windows
        String filename = file.replace('\\', '/');

        // Cambiamos los espacios por %20
        filename = filename.replace(" ", "%20"); //$NON-NLS-1$ //$NON-NLS-2$

        final URI uri = new URI(filename);

        // Comprobamos si es un esquema soportado
        final String scheme = uri.getScheme();
        for (final String element : SUPPORTED_URI_SCHEMES) {
            if (element.equals(scheme)) {
                return uri;
            }
        }

        // Si el esquema es nulo, aun puede ser un nombre de fichero valido
        if (scheme == null) {
            return createURI("file://" + filename); //$NON-NLS-1$
        }

        // Miramos si el esquema es una letra, en cuyo caso seguro que es una
        // unidad de Windows ("C:", "D:", etc.), y le anado el file://
        if (scheme.length() == 1 && Character.isLetter((char) scheme.getBytes()[0])) {
            return createURI("file://" + filename); //$NON-NLS-1$
        }

        throw new IllegalArgumentException("Formato de URI valido pero no soportado '" + filename + "'"); //$NON-NLS-1$ //$NON-NLS-2$

    }

    /** Obtiene el flujo de entrada de un fichero (para su lectura) a partir de su URI.
     * @param uri URI del fichero a leer
     * @return Flujo de entrada hacia el contenido del fichero
     * @throws IOException Cuando ocurre cualquier problema obteniendo el flujo*/
    static InputStream loadFile(final URI uri) throws IOException {

        if (uri == null) {
            throw new IllegalArgumentException("Se ha pedido el contenido de una URI nula"); //$NON-NLS-1$
        }

        if (uri.getScheme().equals("file")) { //$NON-NLS-1$
            // Es un fichero en disco. Las URL de Java no soportan file://, con
            // lo que hay que diferenciarlo a mano

            // Retiramos el "file://" de la uri
            String path = uri.getSchemeSpecificPart();
            if (path.startsWith("//")) { //$NON-NLS-1$
                path = path.substring(2);
            }
            return new BufferedInputStream(new FileInputStream(new File(path)));

        }
        // Es una URL
        final InputStream tmpStream = new BufferedInputStream(uri.toURL().openStream());

        // Las firmas via URL fallan en la descarga por temas de Sun, asi que descargamos primero
        // y devolvemos un Stream contra un array de bytes
        final byte[] tmpBuffer = getDataFromInputStream(tmpStream);

        return new java.io.ByteArrayInputStream(tmpBuffer);

    }

    /** Lee un flujo de datos de entrada y los recupera en forma de array de bytes. Este
     * m&eacute;todo consume pero no cierra el flujo de datos.
     * No cierra el flujo de datos de entrada.
     * @param input Flujo de donde se toman los datos.
     * @return Los datos obtenidos del flujo.
     * @throws IOException Si ocurre cualquier error durante la lectura de datos */
    static byte[] getDataFromInputStream(final InputStream input) throws IOException {
        int nBytes = 0;
        final byte[] buffer = new byte[1024];
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((nBytes = input.read(buffer)) != -1) {
            baos.write(buffer, 0, nBytes);
        }
        return baos.toByteArray();
    }

    /** Crea una URL a partir de una URL base y un nombre de fichero.
     * @param urlBase URL base del fichero.
     * @param filename Nombre del fichero.
     * @return URL de referencia directa al fichero. */
    static URL createURLFile(final URL urlBase, final String filename) {
        try {
            // TODO: Tratar el caso de urls con caracteres especiales
            String codeBase = urlBase.toString();
            if (!codeBase.endsWith("/") && !codeBase.endsWith("\\")) { //$NON-NLS-1$ //$NON-NLS-2$
                codeBase = codeBase + "/"; //$NON-NLS-1$
            }
            return AOBootUtil.createURI(codeBase + filename).toURL();
        }
        catch (final Exception e) {
            LOGGER.severe("No se pudo crear la referencia al fichero '" + filename + "': " + e); //$NON-NLS-1$ //$NON-NLS-2$
            return null;
        }
    }

    /** Copia un fichero.
     * @param source Fichero origen con el contenido que queremos copiar.
     * @param dest Fichero destino de los datos.
     * @return Devuelve <code>true</code> si la operac&oacute;n finaliza correctamente, <code>false</code> en caso contrario. */
    static boolean copyFile(final File source, final File dest) {
        if (source == null || dest == null) {
            return false;
        }

        // Si no existe el directorio del fichero destino, lo creamos
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }

        // Copiamos el directorio
        try {
            final FileChannel in = new FileInputStream(source).getChannel();
            final FileChannel out = new FileOutputStream(dest).getChannel();
            final MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, in.size());
            out.write(buf);

            // Cerramos los canales sin preocuparnos de que lo haga correctamente
            try {
                in.close();
            }
            catch (final Exception e) {
                // Ignoramos los errores en el cierre
            }
            try {
                out.close();
            }
            catch (final Exception e) {
                // Ignoramos los errores en el cierre
            }
        }
        catch (final Exception e) {
            LOGGER.severe(
        		"No se ha podido copiar el fichero origen '" + source.getName() //$NON-NLS-1$
                  + "' al destino '" //$NON-NLS-1$
                  + dest.getName()
                  + "': " //$NON-NLS-1$
                  + e
            );
            return false;
        }
        return true;
    }

    /** Obtiene un ClassLoader que no incluye URL que no referencien directamente a ficheros JAR.
     * @return ClassLoader sin URL adicionales a directorios sueltos Web
     */
    static ClassLoader getCleanClassLoader() {
        ClassLoader classLoader = AOBootUtil.class.getClassLoader();
        if (classLoader instanceof URLClassLoader && !classLoader.getClass().toString().contains("sun.plugin2.applet.JNLP2ClassLoader")) { //$NON-NLS-1$
        	final List<URL> urls = new ArrayList<URL>();
        	for (final URL url : ((URLClassLoader) classLoader).getURLs()) {
        		if (url.toString().endsWith(".jar")) { //$NON-NLS-1$
        			urls.add(url);
        		}
        	}
        	classLoader = new URLClassLoader(urls.toArray(new URL[0]));
        }
        return classLoader;
    }

}
