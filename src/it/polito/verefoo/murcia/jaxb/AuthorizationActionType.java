//
// Questo file è stato generato dall'architettura JavaTM per XML Binding (JAXB) Reference Implementation, v2.2.11 
// Vedere <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Qualsiasi modifica a questo file andrà persa durante la ricompilazione dello schema di origine. 
// Generato il: 2019.09.02 alle 08:15:42 PM CEST 
//


package it.polito.verefoo.murcia.jaxb;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java per AuthorizationActionType.
 * 
 * <p>Il seguente frammento di schema specifica il contenuto previsto contenuto in questa classe.
 * <p>
 * <pre>
 * &lt;simpleType name="AuthorizationActionType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="ALLOW"/&gt;
 *     &lt;enumeration value="DENY"/&gt;
 *     &lt;enumeration value="PUSH"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "AuthorizationActionType")
@XmlEnum
public enum AuthorizationActionType {

    ALLOW,
    DENY,
    PUSH;

    public String value() {
        return name();
    }

    public static AuthorizationActionType fromValue(String v) {
        return valueOf(v);
    }

}
