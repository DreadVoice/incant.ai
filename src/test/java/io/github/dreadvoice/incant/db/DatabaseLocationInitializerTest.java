package io.github.dreadvoice.incant.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class DatabaseLocationInitializerTest {

    @TempDir
    private Path root;

    @Test
    void createsTheDirectoryHoldingTheDatabase() {
        Path database = configuredDatabase();

        run(database);

        assertThat(database.getParent()).isDirectory();
    }

    @Test
    void movesALegacyDatabaseToTheConfiguredLocation() throws IOException {
        Path legacy = writeLegacyDatabase("legacy contents");
        Path database = configuredDatabase();

        run(database);

        assertThat(database).hasContent("legacy contents");
        assertThat(legacy).doesNotExist();
    }

    @Test
    void movesTheWriteAheadLogAlongsideTheLegacyDatabase() throws IOException {
        Path legacy = writeLegacyDatabase("legacy contents");
        Files.writeString(legacy.resolveSibling("incant.db-wal"), "pending writes");
        Path database = configuredDatabase();

        run(database);

        assertThat(database.resolveSibling("incant.db-wal")).hasContent("pending writes");
    }

    @Test
    void keepsAnExistingDatabaseInsteadOfOverwritingIt() throws IOException {
        Path legacy = writeLegacyDatabase("legacy contents");
        Path database = configuredDatabase();
        Files.createDirectories(database.getParent());
        Files.writeString(database, "current contents");

        run(database);

        assertThat(database).hasContent("current contents");
        assertThat(legacy).hasContent("legacy contents");
    }

    @Test
    void leavesTheDatabaseAloneWhenThereIsNothingToMigrate() {
        Path database = configuredDatabase();

        run(database);

        assertThat(database).doesNotExist();
    }

    private Path configuredDatabase() {
        return root.resolve("home/.incant/incant.db");
    }

    private Path workingDirectory() {
        return root.resolve("workspace");
    }

    private Path writeLegacyDatabase(String contents) throws IOException {
        Path legacy = workingDirectory().resolve("data/incant.db");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, contents);
        return legacy;
    }

    private void run(Path database) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:sqlite:" + database)
                .withProperty("user.dir", workingDirectory().toString());

        DatabaseLocationInitializer initializer = new DatabaseLocationInitializer();
        initializer.setEnvironment(environment);
        initializer.postProcessBeanFactory(new DefaultListableBeanFactory());
    }
}
