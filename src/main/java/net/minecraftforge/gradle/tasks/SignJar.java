package net.minecraftforge.gradle.tasks;

import static net.minecraftforge.gradle.common.Constants.resolveString;
import groovy.lang.Closure;
import groovy.util.MapEntry;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraftforge.gradle.util.ZipFileTree;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

@ParallelizableTask
public class SignJar extends DefaultTask implements PatternFilterable
{
    //@formatter:off
    @Input      private PatternSet patternSet = new PatternSet();
    @Input      private Object     alias;
    @Input      private Object     storePass;
    @Input      private Object     keyPass;
    @Input      private Object     keyStore;
    @InputFile  private Object     inputFile;
    @OutputFile private Object     outputFile;
    //@formatter:on

    @TaskAction
    public void doTask() throws IOException
    {
        final Map<String, Entry<byte[], Long>> ignoredStuff = Maps.newHashMap();
        File input = getInputFile();
        File toSign = new File(getTemporaryDir(), input.getName() + ".unsigned.tmp");
        File signed = new File(getTemporaryDir(), input.getName() + ".signed.tmp");
        File output = getOutputFile();

        // load in input jar, and create temp jar
        processInputJar(input, toSign, ignoredStuff);

        // SIGN!
        getProject().getAnt().invokeMethod("signjar", ImmutableMap.builder()
                .put("alias", getAlias())
                .put("storepass", getStorePass())
                .put("keypass", getKeyPass())
                .put("keystore", getKeyStore())
                .put("jar", toSign.getAbsolutePath())
                .put("signedjar", signed.getAbsolutePath())
                .build()
                );

        // write out
        writeOutputJar(signed, output, ignoredStuff);
    }

    private void processInputJar(File inputJar, File toSign, final Map<String, Entry<byte[], Long>> unsigned) throws IOException
    {
        final Spec<FileTreeElement> spec = patternSet.getAsSpec();

        toSign.getParentFile().mkdirs();
        final JarOutputStream outs = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(toSign)));

        (new ZipFileTree(inputJar)).visit(new FileVisitor() {

            @Override
            public void visitDir(FileVisitDetails details)
            {
                // nothing
            }

            @Override
            @SuppressWarnings("unchecked")
            public void visitFile(FileVisitDetails details)
            {
                try
                {
                    if (spec.isSatisfiedBy(details))
                    {
                        ZipEntry entry = new ZipEntry(details.getPath());
                        entry.setTime(details.getLastModified());
                        outs.putNextEntry(entry);
                        details.copyTo(outs);
                        outs.closeEntry();
                    }
                    else
                    {
                        InputStream stream = details.open();
                        unsigned.put(details.getPath(), new MapEntry(ByteStreams.toByteArray(stream), details.getLastModified()));
                        stream.close();
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }

        });

        outs.close();
    }

    private void writeOutputJar(File signedJar, File outputJar, Map<String, Entry<byte[], Long>> unsigned) throws IOException
    {
        outputJar.getParentFile().mkdirs();

        JarOutputStream outs = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputJar)));

        ZipFile base = new ZipFile(signedJar);
        for (ZipEntry e : Collections.list(base.entries()))
        {
            if (e.isDirectory())
            {
                outs.putNextEntry(e);
            }
            else
            {
                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                outs.putNextEntry(n);
                ByteStreams.copy(base.getInputStream(e), outs);
                outs.closeEntry();
            }
        }
        base.close();

        for (Map.Entry<String, Map.Entry<byte[], Long>> e : unsigned.entrySet())
        {
            ZipEntry n = new ZipEntry(e.getKey());
            n.setTime(e.getValue().getValue());
            outs.putNextEntry(n);
            outs.write(e.getValue().getKey());
            outs.closeEntry();
        }

        outs.close();
    }

    @Override
    public PatternFilterable exclude(String... arg0)
    {
        return patternSet.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Iterable<String> arg0)
    {
        return patternSet.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Spec<FileTreeElement> arg0)
    {
        return patternSet.exclude(arg0);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public PatternFilterable exclude(Closure arg0)
    {
        return patternSet.exclude(arg0);
    }

    @Override
    public Set<String> getExcludes()
    {
        return patternSet.getExcludes();
    }

    @Override
    public Set<String> getIncludes()
    {
        return patternSet.getIncludes();
    }

    @Override
    public PatternFilterable include(String... arg0)
    {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable include(Iterable<String> arg0)
    {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable include(Spec<FileTreeElement> arg0)
    {
        return patternSet.include(arg0);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public PatternFilterable include(Closure arg0)
    {
        return patternSet.include(arg0);
    }

    @Override
    public PatternFilterable setExcludes(Iterable<String> arg0)
    {
        return patternSet.setExcludes(arg0);
    }

    @Override
    public PatternFilterable setIncludes(Iterable<String> arg0)
    {
        return patternSet.setIncludes(arg0);
    }

    public File getInputFile()
    {
        if (inputFile == null)
            return null;
        return getProject().file(inputFile);
    }

    public void setInputFile(Object inputFile)
    {
        this.inputFile = inputFile;
    }

    public File getOutputFile()
    {
        if (outputFile == null)
            return null;
        return getProject().file(outputFile);
    }

    public void setOutputFile(Object outputFile)
    {
        this.outputFile = outputFile;
    }

    public String getAlias()
    {
        return resolveString(alias);
    }

    public void setAlias(Object alias)
    {
        this.alias = alias;
    }

    public String getStorePass()
    {
        return resolveString(storePass);
    }

    public void setStorePass(Object storePass)
    {
        this.storePass = storePass;
    }

    public String getKeyPass()
    {
        return resolveString(keyPass);
    }

    public void setKeyPass(Object keyPass)
    {
        this.keyPass = keyPass;
    }

    public String getKeyStore()
    {
        return resolveString(keyStore);
    }

    public void setKeyStore(Object keyStore)
    {
        this.keyStore = keyStore;
    }
}
