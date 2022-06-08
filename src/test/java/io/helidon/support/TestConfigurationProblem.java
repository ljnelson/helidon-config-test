package io.helidon.support;

import java.util.Collection;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.Configuration;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import org.eclipse.microprofile.config.spi.ConfigSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@AddConfig(key = "setInAddConfig", value = "AddConfig")
@Configuration(configSources = { "test.properties", "main.properties" }) // ordering here does not matter; see comments below
@DisableDiscovery
@HelidonTest
class TestConfigurationProblem {

  private TestConfigurationProblem() {
    super();
  }
  
  @Test
  void testConfigurationProblem() {
    final Config config = ConfigProvider.getConfig();

    // Per specification, there are only three default configuration
    // sources in MicroProfile Config: system properties, environment
    // variables, and any META-INF/microprofile-config.properties
    // classpath resources (there are none in this test project).
    // Because of the minimal dependencies in this test project,
    // Helidon's non-standard support for "application.yaml" is
    // omitted.
    //
    // (It is interesting to note that the ordinals of these
    // ConfigSources when loaded via the @HelidonTest infrastructure
    // are both 100.  They should not be; see
    // https://github.com/eclipse/microprofile-config/blob/1.4/spec/src/main/asciidoc/configsources.asciidoc#default-configsources.)
    //
    // Additionally:
    //
    // * @Configuration above adds two ConfigSources representing the
    //   classpath resource "test.properties" (see
    //   ../../../../resources/test.properties) and the classpath
    //   resource "main.properties" (see
    //   ../../../../../main/resources/main.properties). Each has a
    //   default ordinal of 100 since none is explicitly specified and
    //   a name that is equal to the file URL representing the
    //   resource (so
    //   "file:/Users/xyz/path/to/helidon-test-config/target/test-classes/test.properties"
    //   and
    //   "file:/Users/xyz/path/to/helidon-test-config/target/classes/main.properties").
    //
    // * @AddConfig above creates an in-memory ConfigSource with a
    //   default config_ordinal of 1000 (!) and a name of "Map" (!).
    //
    // The net effect is that there will be exactly five ConfigSources
    // that result from this test.  The in-memory one will be asked
    // for values first.  See comments below on how main.properties
    // and test.properties are sorted out.
    assertEquals(5, ((Collection<?>)config.getConfigSources()).size());
    
    // Note that "setOnlyInTest" is not added via @AddConfig above,
    // and it is not present in the system properties or environment
    // variables or in ../../../../../main/resources/main.properties,
    // so if it is present at all, it must be present in
    // ../../../../resources/test.properties (oh look, it is!):
    assertEquals("test", config.getValue("setOnlyInTest", String.class));

    // Note that "setOnlyInMain" is not added via @AddConfig above,
    // and it is not present in the system properties or environment
    // variables or in ../../../../resources/test.properties, so if it
    // is present at all, it must be present in
    // ../../../../../main/resources/main.properties (oh look, it
    // is!):
    assertEquals("main", config.getValue("setOnlyInMain", String.class));

    // Note that "setInBoth" is set in both
    // ../../../../resources/test.properties and
    // ../../../../../main/resources/main.properties.  Which value
    // will be chosen?  This unfortunately requires a long answer due
    // to the non-deterministic nature of the MicroProfile Config
    // specification.
    //
    // ConfigSources (sources of configuration values) have ordinals.
    // The ConfigSource with the highest ordinal that returns a
    // non-null value for a given property name is the one that will
    // "win".
    //
    // What happens when two ConfigSources have the same ordinal?
    // Their names are used as tiebreakers.
    //
    // How are names chosen?  The specification says this is up to the
    // implementation.  Helidon's MicroProfile Config implementation
    // appears to use the string output of the file-based classpath
    // URL from which a given classpath resource was loaded as its
    // name, and uses the name "Map" for the implicit ConfigSource
    // created by @AddConfig above.
    //
    // What does this all mean?  It means that you would do well to
    // make sure all the ConfigSources in your application have
    // distinct ordinals.
    //
    // How can you change the ordinal of a ConfigSource?  You can
    // actually define a property named config_ordinal and set it to a
    // number.  If you don't do this (which is true 99.99% of the
    // time), your ConfigSource is *supposed* to get a default ordinal
    // of 100.
    //
    // Back to our problem: will main.properties or test.properties
    // "win" when asked for a value for the property named
    // "setInBoth"?  Strictly speaking, since both have the default
    // ordinal of 100, their names will be used to break the tie.  So
    // whichever one's URL comes first lexicographically wins.  Since
    // the string fragment "classes/main.properties" precedes the
    // string fragment "test-classes/test.properties"
    // lexicographically, the ConfigSource representing
    // main.properties with ordinal 100 and the name
    // "file:/Users/xyz/path/to/helidon-test-config/target/classes/main.properties"
    // will "beat" the ConfigSource representing test.properties with
    // ordinal 100 and the name
    // "file:/Users/xyz/path/to/helidon-test-config/target/test-classes/test.properties".
    //
    // But wait, you say, this is *terrible*.  Indeed it is.  I say
    // again: define your ordinals yourself.  The specification
    // certainly dug itself an enormous hole in this regard.
    //
    // So, then: we assert the behavior described above, namely that
    // the source that "wins" here is the one with the
    // lexicographically "earliest" string representation of its
    // file-based URL, because neither file defines an ordinal:
    assertEquals("main", config.getValue("setInBoth", String.class));
    
    // This one's fairly obvious:
    assertEquals("AddConfig", config.getValue("setInAddConfig", String.class));
  }
}
