package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.OutputSink.Propagation;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.common.Output;
import com.google.idea.blaze.common.PrintOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class SyncOutput {
  private final List<IssueOutput> issues = new ArrayList<>();
  private final List<String> messages = new ArrayList<>();

  void install(BlazeContext context) {
    addOutputSink(context, IssueOutput.class, issues::add);
    addOutputSink(context, PrintOutput.class, (it) -> messages.add(it.getText()));
    addOutputSink(context, StatusOutput.class, (it) -> messages.add(it.getStatus()));
    addOutputSink(context, SummaryOutput.class, (it) -> messages.add(it.getText()));
  }

  private <T extends Output> void addOutputSink(BlazeContext context, Class<T> clazz, Consumer<T> consumer) {
    context.addOutputSink(clazz, (it) -> {
      consumer.accept(it);
      return Propagation.Continue;
    });
  }

  public String collectLog() {
    final var builder = new StringBuilder();
    final var separator = String.format("%n%s%n", "=".repeat(100));

    builder.append(separator);
    for (final var element : System.getenv().entrySet()) {
      builder.append(String.format("%s: %s%n", element.getKey(), element.getValue()));
    }

    builder.append(separator);
    for (int i = 0; i < messages.size(); i++) {
      builder.append(String.format("%03d: %s%n", i, messages.get(i)));
    }

    return builder.toString();
  }

  public void assertNoErrors() {
    final var message = String.format(
        "there where errors during the sync, check this log:%n%s",
        collectLog()
    );

    assertWithMessage(message).that(issues).isEmpty();
  }
}
