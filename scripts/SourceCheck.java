import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Compiler syntax pass for every Java source even on machines missing Spring. */
class SourceCheck {
    public static void main(String[] args) throws Exception {
        List<Path> paths;
        try(var all=Files.walk(Path.of("src"))) { paths=all.filter(p->p.toString().endsWith(".java")).toList(); }
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        var compiler=ToolProvider.getSystemJavaCompiler();
        try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,null)) {
            var task=(JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release","21","-proc:none"), null,manager.getJavaFileObjectsFromPaths(paths));
            task.parse();
            diagnostics.getDiagnostics().forEach(System.err::println);
            if(!diagnostics.getDiagnostics().isEmpty()) throw new IllegalStateException("Parser findings");
            System.out.println("PASS javac syntax parse on "+paths.size()+" Java sources (dependency type checks excluded)");
        }
    }
}
