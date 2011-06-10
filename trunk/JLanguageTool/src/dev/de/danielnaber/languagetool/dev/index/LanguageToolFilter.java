package de.danielnaber.languagetool.dev.index;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeSource;

import de.danielnaber.languagetool.AnalyzedSentence;
import de.danielnaber.languagetool.AnalyzedToken;
import de.danielnaber.languagetool.AnalyzedTokenReadings;
import de.danielnaber.languagetool.JLanguageTool;

public class LanguageToolFilter extends TokenFilter {
  private JLanguageTool lt;

  private Iterator<AnalyzedTokenReadings> tokenIter;

  // stack for POS
  private Stack<String> stack;

  private CharTermAttribute termAtt;

  private OffsetAttribute offsetAtt;

  private PositionIncrementAttribute posIncrAtt;

  private TypeAttribute typeAtt;

  private AttributeSource.State current;

  public static final String POS_PREFIX = "_POS_";

  protected LanguageToolFilter(TokenStream input, JLanguageTool lt) {
    super(input);
    this.lt = lt;
    stack = new Stack<String>();
    termAtt = addAttribute(CharTermAttribute.class);
    offsetAtt = addAttribute(OffsetAttribute.class);
    posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    typeAtt = addAttribute(TypeAttribute.class);
  }

  @Override
  public boolean incrementToken() throws IOException {

    if (stack.size() > 0) {
      String pop = stack.pop();
      restoreState(current);
      termAtt.append(pop);
      posIncrAtt.setPositionIncrement(0);
      typeAtt.setType("pos");
      return true;
    }

    if (tokenIter == null || !tokenIter.hasNext()) {
      // there are no remaining tokens from the current sentence... are there more sentences?
      if (input.incrementToken()) {
        // a new sentence is available: process it.
        AnalyzedSentence sentence = lt.getAnalyzedSentence(termAtt.toString());

        List<AnalyzedTokenReadings> tokenBuffer = Arrays.asList(sentence.getTokens());
        tokenIter = tokenBuffer.iterator();
        /*
         * it should not be possible to have a sentence with 0 words, check just in case. returning
         * EOS isn't the best either, but its the behavior of the original code.
         */
        if (!tokenIter.hasNext())
          return false;
      } else {
        return false; // no more sentences, end of stream!
      }
    }

    // It must clear attributes, as it is creating new tokens.
    clearAttributes();
    AnalyzedTokenReadings tr = tokenIter.next();
    AnalyzedToken at = tr.getAnalyzedToken(0);

    // add POS tag for sentence start.
    if (tr.isSentStart()) {
      termAtt.append(POS_PREFIX + tr.getAnalyzedToken(0).getPOSTag());
      return true;
    }

    // by pass the white spaces.
    if (tr.isWhitespace()) {
      return this.incrementToken();
    }

    offsetAtt.setOffset(tr.getStartPos(), tr.getStartPos() + at.getToken().length());

    for (int i = 0; i < tr.getReadingsLength(); i++) {
      at = tr.getAnalyzedToken(i);
      if (at.getPOSTag() != null) {
        stack.push(POS_PREFIX + at.getPOSTag());
      }
    }

    current = captureState();
    termAtt.append(tr.getAnalyzedToken(0).getToken());

    return true;

  }
}
