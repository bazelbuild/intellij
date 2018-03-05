package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.lang.buildfile.psi.*;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

import static com.intellij.patterns.PlatformPatterns.psiComment;
import static com.intellij.patterns.PlatformPatterns.psiElement;

public class StructAttributeCompletionContributor extends CompletionContributor {
    public StructAttributeCompletionContributor() {
        extend(
                CompletionType.BASIC,
                IDENTIFIER_PATH,
                STRUCT_PARAMETERS_COMPLETION_PROVIDER
        );
    }

    private static final ElementPattern<PsiElement> IDENTIFIER_PATH = psiElement()
            .withLanguage(BuildFileLanguage.INSTANCE)
            .inside(psiElement(ReferenceExpression.class))
            .andNot(psiComment())
            .and(psiElement()).afterLeaf(".");

    private static final CompletionProvider<CompletionParameters> STRUCT_PARAMETERS_COMPLETION_PROVIDER = new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext processingContext, @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            List<ReferenceExpression> path = buildIdentifierPath(position);
            if(path == null || path.size() < 2)
                return;
            // the last element in the path is what we are doing completion for.
            ArgumentList argumentList = resolveStructArgumentsFor(path.subList(0, path.size()-1));
            if (argumentList != null) for (Argument argument : argumentList.getArguments()) {
                if (canViewArgument(argument, position))
                    result.addElement(LookupElementBuilder.create(argument.nonNullName()).withIcon(AllIcons.Nodes.Parameter));
            }
        }
    };


    /**
     *
     * @param identifiers a list of identifier as they appear in code --e.g., `some_struct.some_nested_struct.`. This shouldn't be a complete path as it's used
     *                    for code completion.
     * @return the argument list for the path.
     */
    @Nullable
    private static ArgumentList resolveStructArgumentsFor(List<ReferenceExpression> identifiers) {
        PsiElement root = resolveElement(identifiers.get(0));
        if (root != null) {
            ArgumentList argumentList = resolveStructArgumentsFor(root);
            for (ReferenceExpression identifier : identifiers.subList(1, identifiers.size())) {
                if (argumentList == null) {
                    return null;
                }
                argumentList = selectIdentifierFrom(argumentList, identifier);
            }
            return argumentList;
        }
        return null;
    }

    /**
     * Attempt to match an identifier in an argumentList to produce another argument list. If the argument cannot be directly resolved it has to be resolved
     * via {@link StructAttributeCompletionContributor#resolveElement(PsiElement)} and {@link StructAttributeCompletionContributor#resolveStructArgumentsFor(PsiElement)}.
     * @param argumentList the argumentList to find the identifier in.
     * @param identifier the identifier we are trying to match.
     * @return the argument list for the identifier.
     */
    @Nullable
    private static ArgumentList selectIdentifierFrom(ArgumentList argumentList, ReferenceExpression identifier) {
        String symbolName = identifier.getReferencedName();
        for (Argument argument : argumentList.getArguments()) {
            if (canViewArgument(argument, identifier) && argument.getFirstChild().getText().equals(symbolName)) {
                Expression value = argument.getValue();
                if (value instanceof FuncallExpression) {
                    return ((FuncallExpression) value).getArgList();
                } else if (value != null) {
                    PsiElement psiElement = resolveElement(value.getOriginalElement());
                    if (psiElement != null) {
                        return resolveStructArgumentsFor(psiElement);
                    }
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * @return if the argument is private to it's file or globally visible.
     */
    private static boolean isPublicArgument(Argument argument) {
        return !argument.getFirstChild().getText().startsWith("_");
    }

    /**
     *
     * @param sourceElement A source element that needs to be matched with a foreign reference or perhaps the origin position in a source file for completions.
     * @param argument The argument that the
     * @return weather the sourceElement can view the argument.
     */
    private static boolean canViewArgument(Argument argument, PsiElement sourceElement) {
        return argument.getContainingFile() != null &&
                (argument.getContainingFile().equals(sourceElement.getContainingFile()) || isPublicArgument(argument));
    }

    @Nullable
    private static ArgumentList resolveStructArgumentsFor(PsiElement element) {
        // going via instanceof is brittle, this works reliably.
        PsiElement maybeFuncCall = element.getParent().getLastChild();
        PsiElement firstChild = maybeFuncCall.getFirstChild();
        if (firstChild.getText().equals("struct")) {
            PsiElement argumentList = maybeFuncCall.getLastChild();
            if (argumentList instanceof ArgumentList) return (ArgumentList) argumentList;
        }
        return null;
    }

    /**
     * Find the canonical representation of an element. If the identifier does not reference something in another file use the {@link PsiReference#resolve()}
     * method to resolve it locally. Otherwise try to find it in another file,  this resolution works by inspecting the `load` statements in the file of the
     * input {@link PsiElement}.
     *
     * @return The resolved form of the element.
     */
    @Nullable
    private static PsiElement resolveElement(PsiElement identifier) {
        String symbolName = identifier.getFirstChild().getText();
        for (LoadStatement loadStatement : PsiUtils.findAllChildrenOfClassRecursive(identifier.getContainingFile(), LoadStatement.class)) {
            for (LoadedSymbol loadedSymbol : PsiUtils.findAllChildrenOfClassRecursive(loadStatement, LoadedSymbol.class)) {
                PsiElement psiElement = mapResolvedLoadedSymbol(loadedSymbol, (name, element) -> symbolName.equals(name) ? element : null);
                if (psiElement != null) return psiElement;
            }
        }

        // This must happen after we have verified the referenced symbol isn't foreign.
        PsiReference reference = identifier.getReference();
        if (reference != null) {
            PsiElement resolve = reference.resolve();
            if (resolve != null) {
                return resolve;
            }
        }

        return null;
    }

    /**
     * A {@link LoadedSymbol} symbol is either a {@link StringLiteral} or it is an alias, flatten this out and pass it onto a mapping function.
     *
     * @return If the mapping function is called the result of the mapping operation. the mapping function might not be called, in which case null. The mapping
     * function is free to return null as well.
     */
    @Nullable
    private static PsiElement mapResolvedLoadedSymbol(LoadedSymbol loadedSymbol, BiFunction<String, PsiElement, PsiElement> consumer) {
        PsiElement firstChild = loadedSymbol.getFirstChild();
        if (firstChild instanceof AssignmentStatement) {
            AssignmentStatement assignmentStatement = (AssignmentStatement) firstChild;
            TargetExpression identifierExpression = assignmentStatement.getLeftHandSideExpression();
            if (identifierExpression != null && assignmentStatement.getLastChild() instanceof StringLiteral) {
                return consumer.apply(
                        identifierExpression.getFirstChild().getText(),
                        ((StringLiteral) assignmentStatement.getLastChild()).getReferencedElement()
                );
            }
        } else if (firstChild instanceof StringLiteral) {
            return consumer.apply(
                    ((StringLiteral) firstChild).getStringContents(),
                    ((StringLiteral) firstChild).getReferencedElement()
            );
        }
        return null;
    }

    /**
     * Flatten a {@link DotExpression} tree into a sequence of Reference expressions.
     *
     * @return A path of ReferenceExpression in the order the appear in text form.
     */
    @Nullable
    private static List<ReferenceExpression> buildIdentifierPath(PsiElement position) {
        PsiElement outerMostDotExpr = findOutermostDescendantOfType(DotExpression.class, position);
        return outerMostDotExpr == null ? null : PsiUtils.findAllChildrenOfClassRecursive(outerMostDotExpr, ReferenceExpression.class);
    }


    @Nullable
    private static PsiElement findOutermostDescendantOfType(Class<?> clazz, PsiElement start) {
        PsiElement parent = start.getParent();
        PsiElement lastExpectedType = null;
        while (!(parent instanceof PsiFile)) {
            if (parent.getClass().equals(clazz)) {
                lastExpectedType = parent;
            }
            parent = parent.getParent();
        }
        return lastExpectedType;
    }
}
