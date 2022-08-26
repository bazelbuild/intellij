package com.google.idea.blaze.aspect;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.collect.Lists.newArrayList;

public class CargoTomlBuilder {
    final Options options;

    CargoTomlBuilder(Options options) {
        this.options = options;
    }

    static final class Options {
        String name;
        String edition;
        Path rootPath;
        Path binPath;
        Path libPath;
        List<String> pathDeps;
        List<String> externalDeps;
        Path outputManifest;
    }

    static Options parseArgs(String[] args) {
        Options options = new Options();
        options.name = OptionParser.parseSingleOption(args, "name", x -> x);
        options.edition = OptionParser.parseSingleOption(args, "edition", x -> x);
        options.rootPath = OptionParser.parseSingleOption(args, "root-path", Paths::get);
        options.binPath = OptionParser.parseSingleOption(args, "bin-path", Paths::get);
        options.libPath = OptionParser.parseSingleOption(args, "lib-path", Paths::get);
        options.pathDeps = OptionParser.parseSingleOption(args, "path-deps", CargoTomlBuilder::parseStringList);
        options.externalDeps = OptionParser.parseSingleOption(args, "external-deps", CargoTomlBuilder::parseStringList);
        options.outputManifest =
                OptionParser.parseSingleOption(
                        args, "output-manifest", string -> FileSystems.getDefault().getPath(string));
        return options;
    }

    static List<String> parseStringList(String raw) {
        return raw.isEmpty() ? Collections.emptyList() : Arrays.asList(raw.split(":"));
    }

    void writeCargoToml() throws IOException {
        try (OutputStream out = Files.newOutputStream(options.outputManifest)) {
            out.write(constructCargoToml().getBytes(StandardCharsets.UTF_8));
        }
    }

    private String constructCargoToml() {
        Config cargoToml = Config.inMemory();

        Config pkg = cargoToml.createSubConfig();
        cargoToml.set("package", pkg);
        pkg.set("name", options.name);
        pkg.set("version", "0.0.0");
        // TODO(alexjpwalker): remove 'edition' field once https://github.com/intellij-rust/intellij-rust/issues/4907
        //  is actually fixed (at the time of writing, the issue is closed, but still reproducible)
        pkg.set("edition", options.edition);

        Config deps = cargoToml.createSubConfig();
        cargoToml.set("dependencies", deps);
        if (options.pathDeps != null) {
            options.pathDeps.forEach(dep -> {
                String[] nameAndPath = dep.split("=");
                String name = nameAndPath[0];
                String path = nameAndPath[1];
                Config pathObj = Config.inMemory();
                pathObj.set("path", path);
                deps.set(name, pathObj);
            });
        }
        if (options.externalDeps != null) {
            options.externalDeps.forEach(dep -> {
                String[] nameAndVersion = dep.split("=");
                String name = nameAndVersion[0];
                String version = nameAndVersion[1];
                deps.set(name, version);
            });
        }

        if (options.libPath != null) {
            Config lib = cargoToml.createSubConfig();
            cargoToml.set("lib", lib);
            lib.set("path", options.rootPath.relativize(options.libPath).toString());
        } else if (options.binPath != null) {
            Config bin = cargoToml.createSubConfig();
            cargoToml.set("bin", newArrayList(bin));
            String binPathString = options.binPath.toString();
            bin.set("name", binPathString.substring(0, binPathString.length() - ".rs".length()));
            bin.set("path", options.rootPath.relativize(options.binPath).toString());
        }
        return new TomlWriter().writeToString(cargoToml.unmodifiable());
    }

    private static final Logger logger = Logger.getLogger(CargoTomlBuilder.class.getName());

    public static void main(String[] args) throws Exception {
        Options options = parseArgs(args);
        Preconditions.checkNotNull(options.name);
        Preconditions.checkNotNull(options.outputManifest);

        try {
            CargoTomlBuilder cargoTomlBuilder = new CargoTomlBuilder(options);
            cargoTomlBuilder.writeCargoToml();
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Error writing Cargo manifest", e);
            System.exit(1);
        }
        System.exit(0);
    }
}
