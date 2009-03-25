package com.intellij.refactoring.util;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TextOccurrencesUtil {
  private TextOccurrencesUtil() {
  }

  public static void addTextOccurences(@NotNull PsiElement element,
                                       @NotNull String stringToSearch,
                                       @NotNull GlobalSearchScope searchScope,
                                       @NotNull final List<UsageInfo> results,
                                       @NotNull final UsageInfoFactory factory) {
    processTextOccurences(element, stringToSearch, searchScope, new Processor<UsageInfo>() {
      public boolean process(UsageInfo t) {
        results.add(t);
        return true;
      }
    }, factory);
  }

  public static void processTextOccurences(@NotNull PsiElement element,
                                           @NotNull String stringToSearch,
                                           @NotNull GlobalSearchScope searchScope,
                                           @NotNull final Processor<UsageInfo> processor,
                                           @NotNull final UsageInfoFactory factory) {
    PsiSearchHelper helper = element.getManager().getSearchHelper();

    helper.processUsagesInNonJavaFiles(element, stringToSearch, new PsiNonJavaFileReferenceProcessor() {
      public boolean process(PsiFile psiFile, int startOffset, int endOffset) {
        UsageInfo usageInfo = factory.createUsageInfo(psiFile, startOffset, endOffset);
        return usageInfo == null || processor.process(usageInfo);
      }
    }, searchScope);
  }

  private static boolean processStringLiteralsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope, PsiSearchHelper helper, final Processor<PsiElement> processor) {
    TextOccurenceProcessor occurenceProcessor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
        final ASTNode node = element.getNode();
        if (node != null && definition.getStringLiteralElements().contains(node.getElementType())) {
          return processor.process(element);
        }
        return true;
      }
    };

    return helper.processElementsWithWord(occurenceProcessor,
                                   searchScope,
                                   identifier,
                                   UsageSearchContext.IN_STRINGS,
                                   true);
  }

  public static boolean processUsagesInStringsAndComments(@NotNull final PsiElement element,
                                                          @NotNull final String stringToSearch,
                                                          final boolean ignoreReferences,
                                                          @NotNull final PairProcessor<PsiElement, TextRange> processor) {
    PsiManager manager = element.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    SearchScope scope = element.getUseScope();
    scope = scope.intersectWith(GlobalSearchScope.projectScope(manager.getProject()));
    Processor<PsiElement> commentOrLiteralProcessor = new Processor<PsiElement>() {
      public boolean process(PsiElement literal) {
        return processTextIn(literal, stringToSearch, ignoreReferences, processor);
      }
    };
    return processStringLiteralsContainingIdentifier(stringToSearch, scope, helper, commentOrLiteralProcessor) &&
           helper.processCommentsContainingIdentifier(stringToSearch, scope, commentOrLiteralProcessor);
  }

  public static void addUsagesInStringsAndComments(@NotNull PsiElement element,
                                                   @NotNull String stringToSearch,
                                                   @NotNull final List<UsageInfo> results,
                                                   @NotNull final UsageInfoFactory factory) {
    processUsagesInStringsAndComments(element, stringToSearch, false, new PairProcessor<PsiElement, TextRange>() {
      public boolean process(PsiElement commentOrLiteral, TextRange textRange) {
        UsageInfo usageInfo = factory.createUsageInfo(commentOrLiteral, textRange.getStartOffset(), textRange.getEndOffset());
        if (usageInfo != null) {
          results.add(usageInfo);
        }
        return true;
      }
    });
  }

  private static boolean processTextIn(PsiElement scope, String stringToSearch, final boolean ignoreReferences, PairProcessor<PsiElement, TextRange> processor) {
    String text = scope.getText();
    for (int offset = 0; offset < text.length(); offset++) {
      offset = text.indexOf(stringToSearch, offset);
      if (offset < 0) break;
      final PsiReference referenceAt = scope.findReferenceAt(offset);
      if (!ignoreReferences && referenceAt != null && referenceAt.resolve() != null) continue;

      if (offset > 0) {
        char c = text.charAt(offset - 1);
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          if (offset < 2 || text.charAt(offset - 2) != '\\') continue;  //escape sequence
        }
      }

      if (offset + stringToSearch.length() < text.length()) {
        char c = text.charAt(offset + stringToSearch.length());
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }

      TextRange textRange = new TextRange(offset, offset + stringToSearch.length());
      if (!processor.process(scope, textRange)) {
        return false;
      }

      offset += stringToSearch.length();
    }
    return true;
  }

  public static boolean isSearchTextOccurencesEnabled(@NotNull PsiElement element) {
    return ElementDescriptionUtil.getElementDescription(element, NonCodeSearchDescriptionLocation.NON_JAVA) != null;
  }

  public interface UsageInfoFactory {
    UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset);
  }
}
