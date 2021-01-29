//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2020.11.09 at 03:29:54 PM CST 
//


package com.webank.wecube.platform.core.service.plugin.xml.register;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;


/**
 * <p>Java class for dockerType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="dockerType">
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;http://www.w3.org/2001/XMLSchema>string">
 *       &lt;attribute name="imageName" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="containerName" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="portBindings" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="volumeBindings" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="envVariables" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "dockerType", propOrder = {
    "value"
})
public class DockerType {

    @XmlValue
    protected String value;
    @XmlAttribute(name = "imageName", required = true)
    protected String imageName;
    @XmlAttribute(name = "containerName", required = true)
    protected String containerName;
    @XmlAttribute(name = "portBindings", required = true)
    protected String portBindings;
    @XmlAttribute(name = "volumeBindings")
    protected String volumeBindings;
    @XmlAttribute(name = "envVariables")
    protected String envVariables;

    /**
     * Gets the value of the value property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Gets the value of the imageName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getImageName() {
        return imageName;
    }

    /**
     * Sets the value of the imageName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setImageName(String value) {
        this.imageName = value;
    }

    /**
     * Gets the value of the containerName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContainerName() {
        return containerName;
    }

    /**
     * Sets the value of the containerName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContainerName(String value) {
        this.containerName = value;
    }

    /**
     * Gets the value of the portBindings property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPortBindings() {
        return portBindings;
    }

    /**
     * Sets the value of the portBindings property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPortBindings(String value) {
        this.portBindings = value;
    }

    /**
     * Gets the value of the volumeBindings property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVolumeBindings() {
        return volumeBindings;
    }

    /**
     * Sets the value of the volumeBindings property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVolumeBindings(String value) {
        this.volumeBindings = value;
    }

    /**
     * Gets the value of the envVariables property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEnvVariables() {
        return envVariables;
    }

    /**
     * Sets the value of the envVariables property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEnvVariables(String value) {
        this.envVariables = value;
    }

}
