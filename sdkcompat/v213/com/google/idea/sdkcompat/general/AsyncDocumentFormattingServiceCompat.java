package com.google.idea.sdkcompat.general;

import com.intellij.formatting.service.AsyncDocumentFormattingService;

/**
 * #api211: remove this class and replace its references with {@link AsyncDocumentFormattingService}
 */
public abstract class AsyncDocumentFormattingServiceCompat extends AsyncDocumentFormattingService {}
