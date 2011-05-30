/*
 * Este fichero forma parte del Cliente @firma. 
 * El Cliente @firma es un aplicativo de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde www.ctt.map.es.
 * Copyright 2009,2010,2011 Gobierno de Espana
 * Este fichero se distribuye bajo licencia GPL version 3 segun las
 * condiciones que figuran en el fichero 'licence' que se acompana. Si se distribuyera este 
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 */

package es.gob.afirma.keystores;

import java.lang.reflect.Field;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;

import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import com.sun.jndi.toolkit.dir.SearchFilter;

import es.gob.afirma.misc.AOUtil;

/**
 * Utilidades para le manejo de almacenes de claves y certificados.
 */
public final class KeyStoreUtilities {

	private KeyStoreUtilities() {
	}

	/**
	 * Crea las l&iacute;neas de configuraci&oacute;n para el proveedor PKCS#11
	 * de Sun.
	 * 
	 * @param lib
	 *            Nombre (con o sin ruta) de la biblioteca PKCS#11
	 * @param name
	 *            Nombre que queremos tenga el proveedor. CUIDADO: SunPKCS11
	 *            a&ntilde;ade el prefijo <i>SunPKCS11-</i>.
	 * @return Fichero con las propiedades de configuracion del proveedor
	 *         PKCS#11 de Sun para acceder al KeyStore de un token generico.
	 */
	static final String createPKCS11ConfigFile(final String lib, String name,
			final Integer slot) {
		if (name == null)
			name = "AFIRMA-PKCS11";
		final StringBuilder buffer = new StringBuilder("library=");

		// TODO: Ir uno a uno en el ApplicationPath de Java hasta que
		// encontremos la biblioteca, en vez de mirar directamente en
		// system32 y usr/lib

		// Si la biblioteca no existe directamente es que viene sin Path
		// Mozilla devuelve las bibliotecas sin Path
		if (!new java.io.File(lib).exists()) {
			String sysLibDir = AOUtil.getSystemLibDir();
			if (!sysLibDir.endsWith(java.io.File.separator))
				sysLibDir += java.io.File.separator;
			buffer.append(sysLibDir);
		}

		buffer.append(lib)
				.append("\r\n")
				// Ignoramos la descripcion que se nos proporciona, ya que el
				// proveedor PKCS#11 de Sun
				// falla si llegan espacios o caracteres raros
				.append("name=").append(name).append("\r\n")
				.append("showInfo=true\r\n");

		if (slot != null) {
			buffer.append("slot=").append(slot);
		}

		Logger.getLogger("es.gob.afirma").info(
				"Creada configuracion PKCS#11:\r\n" + buffer.toString());
		return buffer.toString();
	}

	static void cleanCAPIDuplicateAliases(final KeyStore keyStore)
			throws Exception {

		Field field = keyStore.getClass().getDeclaredField("keyStoreSpi");
		field.setAccessible(true);
		final KeyStoreSpi keyStoreVeritable = (KeyStoreSpi) field.get(keyStore);

		if ("sun.security.mscapi.KeyStore$MY".equals(keyStoreVeritable
				.getClass().getName())) {
			String alias, hashCode;
			X509Certificate[] certificates;

			field = keyStoreVeritable.getClass().getEnclosingClass()
					.getDeclaredField("entries");
			field.setAccessible(true);
			final Collection<?> entries = (Collection<?>) field
					.get(keyStoreVeritable);

			for (Object entry : entries) {
				field = entry.getClass().getDeclaredField("certChain");
				field.setAccessible(true);
				certificates = (X509Certificate[]) field.get(entry);

				hashCode = Integer.toString(certificates[0].hashCode());

				field = entry.getClass().getDeclaredField("alias");
				field.setAccessible(true);
				alias = (String) field.get(entry);

				if (!alias.equals(hashCode)) {
					field.set(entry, alias.concat(" - ").concat(hashCode));
				}
			} // for
		} // if

	}

	private final static int ALIAS_MAX_LENGTH = 120;

	/**
	 * Obtiene una hashtable con las descripciones usuales de los alias de
	 * certificados (como claves de estas &uacute;ltimas).
	 * 
	 * @param alias
	 *            Alias de los certificados entre los que el usuario debe
	 *            seleccionar uno
	 * @param kss
	 *            Listado de KeyStores de donde se han sacadon los alias (debe
	 *            ser <code>null</code> si se quiere usar el m&eacute;todo para
	 *            seleccionar otra cosa que no sean certificados X.509 (como
	 *            claves de cifrado)
	 * @param keyUsageFilter
	 *            Filtro que determina que certificados se van a mostrar
	 *            seg&uacute;n su <code>KeyUsage</code>
	 * @param checkPrivateKeys
	 *            Indica si se debe comprobar que el certificado tiene clave
	 *            privada o no, para no mostrar aquellos que carezcan de ella
	 * @param checkValidity
	 *            Indica si se debe comprobar la validez temporal de un
	 *            certificado al ser seleccionado
	 * @param showExpiredCertificates
	 *            Indica si se deben o no mostrar los certificados caducados o
	 *            aun no v&aacute;lidos
	 * @param issuerFilter
	 *            Filtro seg&uacute;n la RFC2254 para el emisor del certificado
	 * @param subjectFilter
	 *            Filtro seg&uacute;n la RFC2254 para el titular del certificado
	 * @return Alias seleccionado por el usuario
	 */
	public final static Hashtable<String, String> getAlisasesByFriendlyName(
			final String[] alias, final Vector<KeyStore> kss,
			final Boolean[] keyUsageFilter, final boolean checkPrivateKeys,
			final boolean checkValidity, final boolean showExpiredCertificates,
			final String issuerFilter, final String subjectFilter) {

		final String[] trimmedAliases = alias.clone();

		// Creamos un HashTable con la relacion Alias-Nombre_a_mostrar de los
		// certificados
		Hashtable<String, String> aliassesByFriendlyName = new Hashtable<String, String>(
				trimmedAliases.length);
		for (String trimmedAlias : trimmedAliases) {
			aliassesByFriendlyName.put(trimmedAlias, trimmedAlias);
		}

		String tmpCN;
		String issuerTmpCN;

		X509Certificate tmpCert;
		if (kss != null && kss.size() > 0) {

			KeyStore ks = null;
			for (String al : aliassesByFriendlyName.keySet().toArray(
					new String[aliassesByFriendlyName.size()])) {
				tmpCert = null;

				// Seleccionamos el KeyStore en donde se encuentra el alias
				for (KeyStore tmpKs : kss) {
					try {
						tmpCert = (X509Certificate) tmpKs.getCertificate(al);
					} catch (Exception e) {
						Logger.getLogger("es.gob.afirma").warning("No se ha inicializado el KeyStore indicado: " + e); //$NON-NLS-1$ //$NON-NLS-2$
						continue;
					}
					if (tmpCert != null) {
						ks = tmpKs;
						break;
					}
				}

				// Si no tenemos Store para el alias en curso, pasamos al
				// siguiente alias
				if (ks == null)
					continue;

				if (tmpCert == null)
					Logger.getLogger("es.gob.afirma").warning("El KeyStore no permite extraer el certificado publico para el siguiente alias: " + al); //$NON-NLS-1$ //$NON-NLS-2$

				if (!showExpiredCertificates && tmpCert != null) {
					try {
						tmpCert.checkValidity();
					} catch (Exception e) {
						Logger.getLogger("es.gob.afirma").info( //$NON-NLS-1$
										"Se ocultara el certificado '" + al + "' por no ser valido: " + e //$NON-NLS-1$ //$NON-NLS-2$
								);
						aliassesByFriendlyName.remove(al);
						continue;
					}
				}

				if (checkPrivateKeys && tmpCert != null) {
					try {
						if ("KeychainStore".equals(ks.getType())) {
							PrivateKey key = null;
							try {
								key = (PrivateKey) ks.getKey(al,
										"dummy".toCharArray());
							} catch (final Exception e) {
								throw new UnsupportedOperationException(
										"No se ha podido recuperar directamente la clave privada en Mac OS X",
										e);
							}
							if (key == null)
								throw new UnsupportedOperationException(
										"No se ha podido recuperar directamente la clave privada en Mac OS X");
						} else if (!(ks.getEntry(al,
								new KeyStore.PasswordProtection(new char[0])) instanceof KeyStore.PrivateKeyEntry)) {
							aliassesByFriendlyName.remove(al);
							Logger.getLogger("es.gob.afirma").info( //$NON-NLS-1$
											"El certificado '" + al + "' no era tipo trusted pero su clave tampoco era de tipo privada, no se mostrara" //$NON-NLS-1$ //$NON-NLS-2$
									);
							continue;
						}
					} catch (final UnsupportedOperationException e) {
						aliassesByFriendlyName.remove(al);
						Logger.getLogger("es.gob.afirma").info( //$NON-NLS-1$
										"El certificado '" + al + "' no se mostrara por no soportar operaciones de clave privada" //$NON-NLS-1$ //$NON-NLS-2$
								);
						continue;
					} catch (final Exception e) {
						Logger.getLogger("es.gob.afirma").info( //$NON-NLS-1$
										"Se ha incluido un certificado (" + al + ") con clave privada inaccesible: " + e //$NON-NLS-1$ //$NON-NLS-2$
								);
					}
				}

				if (tmpCert != null
						&& matchesKeyUsageFilter(tmpCert, keyUsageFilter)
						&& KeyStoreUtilities.filterIssuerByRFC2254(
								issuerFilter, tmpCert)
						&& KeyStoreUtilities.filterSubjectByRFC2254(
								subjectFilter, tmpCert)) {
					tmpCN = AOUtil.getCN(tmpCert);
					issuerTmpCN = AOUtil.getCN(tmpCert.getIssuerX500Principal()
							.getName());

					if (tmpCN != null && issuerTmpCN != null) {
						aliassesByFriendlyName
								.put(al,
										tmpCN
												+ " (" + issuerTmpCN + ", " + tmpCert.getSerialNumber() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
					}

					else if (tmpCN != null /* && isValidString(tmpCN) */) {
						aliassesByFriendlyName.put(al, tmpCN);
					} else {
						// Hacemos un trim() antes de insertar, porque los alias
						// de los certificados de las tarjetas
						// ceres terminan con un '\r', que se ve como un
						// caracter extrano
						aliassesByFriendlyName.put(al, al.trim());
					}
				} else {
					// Eliminamos aquellos certificados que no hayan encajado
					Logger.getLogger("es.gob.afirma").info( //$NON-NLS-1$
									"El certificado '" + al + "' no se mostrara por no cumplir el filtro de uso" //$NON-NLS-1$ //$NON-NLS-2$
							);
					aliassesByFriendlyName.remove(al);
				}
			}
		}

		else {

			// Vamos a ver si en vez de un alias nos llega un Principal X.500
			// completo,
			// en cuyo caso es muy largo como para mostrase y mostrariamos solo
			// el
			// CN o una version truncada si no nos cuela como X.500.
			// En este bucle usamos la clave tanto como clave como valor porque
			// asi se ha inicializado
			// el HashTable.
			for (String al : aliassesByFriendlyName.keySet().toArray(
					new String[aliassesByFriendlyName.size()])) {
				final String value = aliassesByFriendlyName.get(al);
				if (value.length() > ALIAS_MAX_LENGTH) {
					tmpCN = AOUtil.getCN(value);
					if (tmpCN != null)
						aliassesByFriendlyName.put(al, tmpCN);
					else
						aliassesByFriendlyName.put(al,
								value.substring(0, ALIAS_MAX_LENGTH - 3)
										+ "..."); //$NON-NLS-1$
				}
				// Hacemos un trim() antes de insertar, porque los alias de los
				// certificados de las tarjetas
				// ceres terminan con un '\r', que se ve como un caracter
				// extrano.
				// Esto ya lo habremos hecho anteriormente si teniamos KeyStore
				else
					aliassesByFriendlyName.put(al, value.trim());
			}
		}

		return aliassesByFriendlyName;

	}

	private static boolean filterSubjectByRFC2254(final String filter,
			final X509Certificate cert) {
		if (cert == null || filter == null)
			return true;
		return filterRFC2254(filter, cert.getSubjectDN().toString());
	}

	private static boolean filterIssuerByRFC2254(final String filter,
			final X509Certificate cert) {
		if (cert == null || filter == null)
			return true;
		return filterRFC2254(filter, cert.getIssuerDN().toString());
	}

	/**
	 * Indica si un nombres LDAP se ajusta a los requisitos de un filtro.
	 * 
	 * @param f
	 *            Filtro seg&uacute;n la RFC2254.
	 * @param name
	 *            Nombre LDAP al que se debe aplicar el filtro.
	 * @return <code>true</code> si el nombre LDAP es nulo o se adec&uacute;a al
	 *         filtro o este &uacute;ltimo es nulo, <code>false</code> en caso
	 *         contrario
	 */
	private static boolean filterRFC2254(final String f, final String name) {
		try {
			return filterRFC2254(f, new LdapName(name));
		} catch (final Exception e) {
			Logger.getLogger("es.gob.afirma").warning(
					"No ha sido posible filtrar el certificado (filtro: '" + f
							+ "', nombre: '" + name
							+ "'), no se eliminara del listado: " + e);
			return true;
		}
	}

	/**
	 * Indica si un nombres LDAP se ajusta a los requisitos de un filtro.
	 * 
	 * @param f
	 *            Filtro seg&uacute;n la RFC2254.
	 * @param name
	 *            Nombre LDAP al que se debe aplicar el filtro.
	 * @return <code>true</code> si el nombre LDAP es nulo o se adec&uacute;a al
	 *         filtro o este &uacute;ltimo es nulo, <code>false</code> en caso
	 *         contrario
	 */
	private static boolean filterRFC2254(final String f, final LdapName name) {
		if (f == null || name == null)
			return true;
		try {
			List<Rdn> rdns = name.getRdns();
			if (rdns == null || (rdns.isEmpty())) {
				Logger.getLogger("es.gob.afirma")
						.warning(
								"El nombre proporcionado para filtrar no contiene atributos, no se mostrara el certificado en el listado");
				return false;
			}
			final Attributes attrs = new BasicAttributes(true);
			for (Rdn rdn : rdns)
				attrs.put(rdn.getType(), rdn.getValue());
			return new SearchFilter(f).check(attrs);
		} catch (final Exception e) {
			Logger.getLogger("es.gob.afirma").warning(
					"No ha sido posible filtrar el certificado (filtro: '" + f
							+ "', nombre: '" + name
							+ "'), no se eliminara del listado: " + e);
			return true;
		}
	}

	/**
	 * Comprueba si el uso un certificado concuerda con un filtro dado.
	 * 
	 * @param cert
	 *            Certificado X.509 que queremos comprobar
	 * @param filter
	 *            Filtro con los bits de uso (<i>KeyUsage</i>) a verificar
	 * @return <code>true</code> si el certificado concuerda con el filtro,
	 *         <code>false</code> en caso contrario
	 */
	public final static boolean matchesKeyUsageFilter(
			final X509Certificate cert, final Boolean[] filter) {
		if (filter == null)
			return true;
		if (cert == null)
			return false;
		if (filter.length == 9) {
			boolean[] certUsage = cert.getKeyUsage();
			if (certUsage != null) {
				for (int j = 0; j < certUsage.length; j++) {
					if (filter[j] != null
							&& filter[j].booleanValue() != certUsage[j])
						return false;
				}
				return true;
			}
		}
		return false;
	}
}
