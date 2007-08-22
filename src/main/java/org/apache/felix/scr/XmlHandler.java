/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr;


import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.felix.scr.parser.KXml2SAXHandler;
import org.apache.felix.scr.parser.ParseException;


/**
 *
 *
 */
public class XmlHandler implements KXml2SAXHandler
{

    public static final String NAMESPACE_URI = "http://www.osgi.org/xmlns/scr/v1.0.0";

    // A reference to the current component
    private ComponentMetadata m_currentComponent;

    // The current service
    private ServiceMetadata m_currentService;

    // A list of component descriptors contained in the file
    private List m_components = new ArrayList();

    // PropertyMetaData whose value attribute is missing, hence has element data
    private PropertyMetadata m_pendingProperty;

    /** Flag for detecting the first element. */
    protected boolean firstElement = true;

    /** Override namespace. */
    protected String overrideNamespace;


    /**
     * Method called when a tag opens
     *
     * @param   uri
     * @param   localName
     * @param   attrib
     * @exception   ParseException
    **/
    public void startElement( String uri, String localName, Properties attrib ) throws ParseException
    {
        // according to the spec, the elements should have the namespace,
        // except when the root element is the "component" element
        // So we check this for the first element, we receive.
        if ( firstElement )
        {
            firstElement = false;
            if ( localName.equals( "component" ) && "".equals( uri ) )
            {
                overrideNamespace = NAMESPACE_URI;
            }
        }

        if ( overrideNamespace != null && "".equals( uri ) )
        {
            uri = overrideNamespace;
        }
        if ( NAMESPACE_URI.equals( uri ) )
        {
            try
            {

                // 112.4.3 Component Element
                if ( localName.equals( "component" ) )
                {

                    // Create a new ComponentMetadata
                    m_currentComponent = new ComponentMetadata();

                    // name attribute is mandatory
                    m_currentComponent.setName( attrib.getProperty( "name" ) );

                    // enabled attribute is optional
                    if ( attrib.getProperty( "enabled" ) != null )
                    {
                        m_currentComponent.setEnabled( attrib.getProperty( "enabled" ).equals( "true" ) );
                    }

                    // immediate attribute is optional
                    if ( attrib.getProperty( "immediate" ) != null )
                    {
                        m_currentComponent.setImmediate( attrib.getProperty( "immediate" ).equals( "true" ) );
                    }

                    // factory attribute is optional
                    if ( attrib.getProperty( "factory" ) != null )
                    {
                        m_currentComponent.setFactoryIdentifier( attrib.getProperty( "factory" ) );
                    }

                    // Add this component to the list
                    m_components.add( m_currentComponent );
                }

                // 112.4.4 Implementation
                else if ( localName.equals( "implementation" ) )
                {
                    // Set the implementation class name (mandatory)
                    m_currentComponent.setImplementationClassName( attrib.getProperty( "class" ) );
                }
                // 112.4.5 Properties and Property Elements
                else if ( localName.equals( "property" ) )
                {
                    PropertyMetadata prop = new PropertyMetadata();

                    // name attribute is mandatory
                    prop.setName( attrib.getProperty( "name" ) );

                    // type attribute is optional
                    if ( attrib.getProperty( "type" ) != null )
                    {
                        prop.setType( attrib.getProperty( "type" ) );
                    }

                    // 112.4.5: If the value attribute is specified, the body of the element is ignored.
                    if ( attrib.getProperty( "value" ) != null )
                    {
                        prop.setValue( attrib.getProperty( "value" ) );
                        m_currentComponent.addProperty( prop );
                    }
                    else
                    {
                        // hold the metadata pending
                        m_pendingProperty = prop;
                    }
                    // TODO: treat the case where a properties file name is provided (p. 292)
                }
                else if ( localName.equals( "properties" ) )
                {
                    // TODO: implement the properties tag
                }
                // 112.4.6 Service Element
                else if ( localName.equals( "service" ) )
                {

                    m_currentService = new ServiceMetadata();

                    // servicefactory attribute is optional
                    if ( attrib.getProperty( "servicefactory" ) != null )
                    {
                        m_currentService.setServiceFactory( attrib.getProperty( "servicefactory" ).equals( "true" ) );
                    }

                    m_currentComponent.setService( m_currentService );
                }
                else if ( localName.equals( "provide" ) )
                {
                    m_currentService.addProvide( attrib.getProperty( "interface" ) );
                }

                // 112.4.7 Reference element
                else if ( localName.equals( "reference" ) )
                {
                    ReferenceMetadata ref = new ReferenceMetadata();
                    ref.setName( attrib.getProperty( "name" ) );
                    ref.setInterface( attrib.getProperty( "interface" ) );

                    // Cardinality
                    if ( attrib.getProperty( "cardinality" ) != null )
                    {
                        ref.setCardinality( attrib.getProperty( "cardinality" ) );
                    }

                    if ( attrib.getProperty( "policy" ) != null )
                    {
                        ref.setPolicy( attrib.getProperty( "policy" ) );
                    }

                    //if
                    ref.setTarget( attrib.getProperty( "target" ) );
                    ref.setBind( attrib.getProperty( "bind" ) );
                    ref.setUnbind( attrib.getProperty( "unbind" ) );

                    m_currentComponent.addDependency( ref );
                }
            }
            catch ( Exception ex )
            {
                ex.printStackTrace();
                throw new ParseException( "Exception during parsing", ex );
            }
        }
    }


    /**
    * Method called when a tag closes
    *
    * @param   uri
    * @param   localName
    * @exception   ParseException
    */
    public void endElement( String uri, String localName )
    {
        if ( overrideNamespace != null && "".equals( uri ) )
        {
            uri = overrideNamespace;
        }

        if ( NAMESPACE_URI.equals( uri ) )
        {
            if ( localName.equals( "component" ) )
            {
                // When the closing tag for a component is found, the component is validated to check if
                // the implementation class has been set
                m_currentComponent.validate();
            }
            else if ( localName.equals( "property" ) && m_pendingProperty != null )
            {
                // 112.4.5 body expected to contain property value
                // if so, the m_pendingProperty field would be null
                // currently, we just ignore this situation
                m_pendingProperty = null;
            }
        }
    }


    /**
    * Called to retrieve the service descriptors
    *
    * @return   A list of service descriptors
    */
    List getComponentMetadataList()
    {
        return m_components;
    }


    /**
     * @see org.apache.felix.scr.parser.KXml2SAXHandler#characters(java.lang.String)
     */
    public void characters( String text )
    {
        // 112.4.5 If the value attribute is not specified, the body must contain one or more values
        if ( m_pendingProperty != null )
        {
            m_pendingProperty.setValues( text );
            m_currentComponent.addProperty( m_pendingProperty );
            m_pendingProperty = null;
        }
    }


    /**
     * @see org.apache.felix.scr.parser.KXml2SAXHandler#processingInstruction(java.lang.String, java.lang.String)
     */
    public void processingInstruction( String target, String data )
    {
        // Not used
    }


    /**
     * @see org.apache.felix.scr.parser.KXml2SAXHandler#setLineNumber(int)
     */
    public void setLineNumber( int lineNumber )
    {
        // Not used
    }


    /**
     * @see org.apache.felix.scr.parser.KXml2SAXHandler#setColumnNumber(int)
     */
    public void setColumnNumber( int columnNumber )
    {
        // Not used
    }
}
