import com.intellij.rt.coverage.data.*;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import java.io.File;
import java.util.*;

/** Read the IntelliJ agent's actual line counters for the two offline suites. */
class CoverageSummary {
    public static void main(String[] files) {
        Map<String,Map<Integer,Boolean>> sources=new TreeMap<>();
        Set<String> included=Set.of("AlertEvent","BillingAdapter","Rules","DeliveryPool");
        for(String file:files) {
            var project=ProjectDataLoader.load(new File(file));
            for(var entry:project.getClasses().entrySet()) {
                String name=entry.getKey().replace('/','.');
                String source=name.substring(name.lastIndexOf('.')+1).split("\\$")[0];
                if(!included.contains(source)) continue;
                var lines=sources.computeIfAbsent(source,k->new TreeMap<>());
                Object[] data=entry.getValue().getLines();
                if(data==null) continue;
                for(Object line:data) if(line instanceof LineData d) {
                    lines.merge(d.getLineNumber(),d.getHits()>0,(a,b)->a||b);
                }
            }
        }
        if(!sources.keySet().equals(included)) throw new AssertionError("Missing coverage data: "+sources.keySet());
        long total=0,hits=0;
        for(var entry:sources.entrySet()) {
            var lines=entry.getValue();
            long covered=lines.values().stream().filter(Boolean::booleanValue).count();
            System.out.printf("%s.java %d/%d instrumented lines%n",entry.getKey(),covered,lines.size());
            total+=lines.size();hits+=covered;
        }
        System.out.printf(Locale.ROOT,"Offline subset %d/%d (%.1f%%)%n",hits,total,100.0*hits/total);
    }
}
