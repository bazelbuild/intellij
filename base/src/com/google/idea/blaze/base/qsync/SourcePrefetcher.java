package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchStats;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProjectListener;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtilRt;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class SourcePrefetcher implements BlazeProjectListener {

  private static final BoolExperiment ENABLED = new BoolExperiment("qsync.prefetch.srcs", false);
  private static final Logger logger = Logger.getInstance(SourcePrefetcher.class);

  private final ExecutorService executorService;
  private final Path workspaceRoot;

  public SourcePrefetcher(ExecutorService executorService, Path workspaceRoot) {
    this.executorService = executorService;
    this.workspaceRoot = workspaceRoot;
  }

  private static String getExtension(Path p) {
    String name = p.getFileName().toString();
    int dotPos = name.lastIndexOf('.');
    if (dotPos == -1) {
      return "";
    }
    return name.substring(dotPos + 1);
  }

  @Override
  public void graphCreated(Context<?> context, BlazeProjectSnapshot instance) {
    if (!ENABLED.getValue()) {
      logger.info("Source prefetcher disabled");
      return;
    }
    List<Path> sourceFiles = instance.graph().getAllSourceFiles();
    context.output(
        PrintOutput.output(
            String.format("Prefetching %d source files; count by ext:", sourceFiles.size())));
    logger.info("Requesting prefetch of %d source files; count by ext:");
    sourceFiles.stream()
        .map(SourcePrefetcher::getExtension)
        .collect(ImmutableMultiset.toImmutableMultiset())
        .entrySet()
        .stream()
        .sorted(Comparator.<Multiset.Entry<String>>comparingInt(e -> e.getCount()).reversed())
        .map(e -> String.format("  %s: %s", e.getElement(), e.getCount()))
        .forEach(logger::info);
    ListenableFuture<PrefetchStats> stats =
        PrefetchService.getInstance()
            .prefetchFiles(
                sourceFiles.stream()
                    .map(workspaceRoot::resolve)
                    .map(Path::toFile)
                    .collect(Collectors.toSet()),
                false,
                false);
    Futures.addCallback(
        stats,
        new FutureCallback<>() {
          @Override
          public void onSuccess(PrefetchStats prefetchStats) {
            context.output(
                PrintOutput.log(
                    "Prefetched %s of source files",
                    StringUtilRt.formatFileSize(prefetchStats.bytesPrefetched())));
            logger.info(
                String.format(
                    "Prefetched %s bytes of source files (%d files); count by ext:",
                    prefetchStats.bytesPrefetched(), prefetchStats.countByExtension().size()));
            prefetchStats.countByExtension().entrySet().stream()
                .sorted(
                    Comparator.<Multiset.Entry<String>>comparingInt(e -> e.getCount()).reversed())
                .map(e -> String.format("  %s: %s", e.getElement(), e.getCount()))
                .forEach(logger::info);
          }

          @Override
          public void onFailure(Throwable throwable) {
            context.handleExceptionAsWarning("Source prefetching encountered problems", throwable);
          }
        },
        executorService);
  }
}
