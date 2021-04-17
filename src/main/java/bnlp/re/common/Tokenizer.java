package bnlp.re.common;

import bnlp.nlp.sentence.SentenceLexer2;
import bnlp.nlp.sentence.TokenInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bozyurt on 10/22/20.
 */
public class Tokenizer {

    public List<TokenInfo> tokenize(String content) throws IOException {
        SentenceLexer2 lexer2 = new SentenceLexer2(content);
        List<TokenInfo> tiList = new ArrayList<>(20);
        TokenInfo ti = null;
        while((ti = lexer2.getNextTI()) != null) {
            tiList.add(ti);
        }
        return tiList;
    }
}
