package io.github.dreadvoice.incant.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DatabaseDirectoryInitializer implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final String URL_PROPERTY = "spring.datasource.url";
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        String url = environment.getProperty(URL_PROPERTY, "");
        if (!url.startsWith(SQLITE_PREFIX)) {
            return;
        }

        Path directory = Path.of(url.substring(SQLITE_PREFIX.length())).toAbsolutePath().getParent();
        if (directory == null) {
            return;
        }

        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("could not create database directory " + directory + ": " + e.getMessage(),
                    e);
        }
    }
}
