package com.vmantek.chimera.db;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jpos.q2.install.ModuleUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class JPosDatabaseAutoConfiguration
{
    @Bean
   	public EntityManagerFactoryBuilder entityManagerFactoryBuilder(
   			JpaVendorAdapter jpaVendorAdapter,
   			JpaProperties jpaProperties,
   			ObjectProvider<PersistenceUnitManager> persistenceUnitManager) {
   		EntityManagerFactoryBuilder builder = new EntityManagerFactoryBuilder(
   				jpaVendorAdapter, jpaProperties.getProperties(),
   				persistenceUnitManager.getIfAvailable());
   		builder.setCallback(factory ->
                                factory.setPersistenceUnitPostProcessors(
                                    new JPosPersistentUnitPostProcessor()));
   		return builder;
   	}

    private class JPosPersistentUnitPostProcessor implements PersistenceUnitPostProcessor
    {
        public static final String MODULES_PKGNAME = "META-INF/org/jpos/ee/modules/";

        @Override
        public void postProcessPersistenceUnitInfo(MutablePersistenceUnitInfo pui)
        {
            List<String> moduleConfigs = null;
            try
            {
                moduleConfigs = ModuleUtils.getModuleEntries(MODULES_PKGNAME);
            }
            catch (IOException e)
            {
                throw new IllegalStateException("Problem fetching Hibernate jPOS modules", e);
            }

            for (String moduleConfig : moduleConfigs)
            {
                SAXReader reader = new SAXReader();

                final URL url = getClass().getClassLoader().getResource(moduleConfig);
                assert url != null;
                final Document doc;
                try
                {
                    doc = reader.read(url);
                }
                catch (DocumentException e)
                {
                    throw new IllegalStateException("Could not initalize module reader", e);
                }

                Element module = doc.getRootElement().element("mappings");
                if (module == null)
                {
                    throw new IllegalStateException("No 'mappings' element in module=" + moduleConfig);
                }

                for (Iterator l = module.elementIterator("mapping"); l.hasNext(); )
                {
                    Element mapping = (Element) l.next();
                    final String resource = mapping.attributeValue("resource");
                    final String clazz = mapping.attributeValue("class");

                    if (resource != null)
                    {
                        if (!pui.getMappingFileNames().contains(resource))
                        {
                            pui.addMappingFileName(resource);
                        }
                    }
                    else if (clazz != null)
                    {
                        if (!pui.getManagedClassNames().contains(clazz))
                        {
                            pui.addManagedClassName(clazz);
                        }
                    }
                    else
                    {
                        throw new IllegalStateException("<mapping> element in configuration specifies no known " +
                                                        "attributes at module " + moduleConfig);
                    }
                }
            }
        }
    }
}
