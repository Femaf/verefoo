//
// Questo file è stato generato dall'architettura JavaTM per XML Binding (JAXB) Reference Implementation, v2.2.11 
// Vedere <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Qualsiasi modifica a questo file andrà persa durante la ricompilazione dello schema di origine. 
// Generato il: 2019.09.02 alle 08:15:42 PM CEST 
//


package it.polito.verefoo.murcia.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java per PolicyDependency complex type.
 * 
 * <p>Il seguente frammento di schema specifica il contenuto previsto contenuto in questa classe.
 * 
 * <pre>
 * &lt;complexType name="PolicyDependency"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://modeliosoft/xsddesigner/a22bd60b-ee3d-425c-8618-beb6a854051a/ITResource.xsd}Dependency"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="configurationCondition" type="{http://modeliosoft/xsddesigner/a22bd60b-ee3d-425c-8618-beb6a854051a/ITResource.xsd}PolicyDependencyCondition"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PolicyDependency", propOrder = {
    "configurationCondition"
})
public class PolicyDependency
    extends Dependency
{

    @XmlElement(required = true)
    protected PolicyDependencyCondition configurationCondition;

    /**
     * Recupera il valore della proprietà configurationCondition.
     * 
     * @return
     *     possible object is
     *     {@link PolicyDependencyCondition }
     *     
     */
    public PolicyDependencyCondition getConfigurationCondition() {
        return configurationCondition;
    }

    /**
     * Imposta il valore della proprietà configurationCondition.
     * 
     * @param value
     *     allowed object is
     *     {@link PolicyDependencyCondition }
     *     
     */
    public void setConfigurationCondition(PolicyDependencyCondition value) {
        this.configurationCondition = value;
    }

}
