// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExternalConsumerContractTest {
    @TempDir
    Path directory;

    private static final String CONSUMER = """
            package example;
            import com.dishanhai.sh_better_ui.kernel.api.*;
            final class Consumer {
                interface Layout extends UIService {}
                interface Theme extends UIService {}
                static final ServiceKey<Layout> LAYOUT = new ServiceKey<>(UiId.parse("example:layout"), Layout.class, 1);
                static final ModuleDescriptor MODULE = ModuleDescriptor.builder().id("example:ui").version("1.0.0").build();
                static %s layout() { %s }
            }
            """;

    @Test
    void anExternalJava17ConsumerCanCompileAgainstOnlyTheKernel() throws Exception {
        var result = compile(CONSUMER.formatted("Layout", "return UnifiedUI.get().require(LAYOUT);"));
        assertTrue(result.success(), () -> result.diagnostics().toString());
    }

    @Test
    void externalSpatialConsumerCompilesAndLinksWithOnlyJava17KernelClasses() throws Exception {
        var result = compile("""
                package example;
                import com.dishanhai.sh_better_ui.kernel.api.*;
                import com.dishanhai.sh_better_ui.spatial.*;
                import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.*;
                public final class Consumer {
                    public static String contract() {
                        var spec = new SpatialScene.Spec(UiId.parse("example:card"), null,
                                Shape.rounded(80, 36, 5), Transform.IDENTITY, 0, 1, true, false);
                        return SpatialUI.KEY.id() + "/" + spec.id();
                    }
                }
                """);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        var kernel = UnifiedUI.class.getProtectionDomain().getCodeSource().getLocation();
        try (var isolated = new URLClassLoader(new URL[]{directory.toUri().toURL(), kernel},
                ClassLoader.getPlatformClassLoader())) {
            var consumer = isolated.loadClass("example.Consumer");
            assertEquals("sh_better_ui:spatial/example:card", consumer.getMethod("contract").invoke(null));
        }
    }

    @Test
    void javacRejectsLookingUpAServiceAsAnotherType() throws Exception {
        var result = compile(CONSUMER.formatted("Theme", "return UnifiedUI.get().require(LAYOUT);"));
        assertFalse(result.success());
        assertEquals(List.of("compiler.err.prob.found.req"), result.errorCodes());
    }

    @Test
    void thePublicFacadeCannotInstallAnEngine() throws Exception {
        var result = compile(CONSUMER.formatted("void", "UnifiedUI.install(null);"));
        assertFalse(result.success());
        assertEquals(List.of("compiler.err.cant.resolve.location.args"), result.errorCodes());
    }

    private Compilation compile(String source) throws Exception {
        Path input = Files.writeString(directory.resolve("Consumer.java"), source);
        Path classpath = Path.of(UnifiedUI.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var args = List.of("--release", "17", "-proc:none", "-classpath", classpath.toString(),
                    "-d", directory.toString());
            var units = files.getJavaFileObjects(input);
            boolean success = compiler.getTask(null, files, diagnostics, args, null, units).call();
            return new Compilation(success, diagnostics.getDiagnostics());
        }
    }

    private record Compilation(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        List<String> errorCodes() {
            return diagnostics.stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(Diagnostic::getCode).toList();
        }
    }
}
