package com.google.idea.blaze.base.util;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public class TemplateWriter {

    private final Path resourcePath;
    private final VelocityEngine velocityEngine;

    public TemplateWriter(Path resourcePath) {
        this.resourcePath = resourcePath;
        this.velocityEngine = new VelocityEngine();
        this.velocityEngine.init(getProperties());
    }

    private Properties getProperties() {
        Properties props = new Properties();
        props.put("resource.loader.file.path", resourcePath.toAbsolutePath().toString());
        props.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem");
        return props;
    }

    public void writeToFile(String templateFilePath, Path outputFile, Map<String, String> variableMap) {
        try {
            org.apache.velocity.Template template = velocityEngine.getTemplate(templateFilePath);
            VelocityContext context = new VelocityContext();
            for (Map.Entry<String, String> entry : variableMap.entrySet()) {
                context.put(entry.getKey(), entry.getValue());
            }
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            FileUtil.writeIfDifferent(outputFile, writer.toString());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error writing template to file", e);
        }
    }
}
