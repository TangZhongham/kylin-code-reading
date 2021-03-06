/* Generated By:JavaCC: Do not edit this line. CommentParser.java */
package org.apache.kylin.query.util;
import java.io.StringReader;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommentParser implements CommentParserConstants {

    private final static Logger logger = LoggerFactory.getLogger(CommentParser.class);


    public static void main(String args[]) throws ParseException {
        System.out.println("Input SQL:");
        Scanner reader = new Scanner(System.in, "UTF-8");
        String sql = reader.nextLine();
        reader.close();
        CommentParser parser = new CommentParser(new StringReader(sql));
        String parseResult = parser.Input();
        System.out.println("Translated SQL:");
        System.out.println(parseResult);
    }

    public CommentParser(String sql) {
        this(new StringReader(sql));
    }

/** Root production. */
  final public String Input() throws ParseException {
    String innerString;
    StringBuilder transformedStr = new StringBuilder();
    label_1:
    while (true) {
      innerString = Expression();
        transformedStr.append(innerString);
      if (jj_2_1(2)) {
        ;
      } else {
        break label_1;
      }
    }
    jj_consume_token(0);
        {if (true) return transformedStr.toString();}
    throw new Error("Missing return statement in function");
  }

/** Brace counting production. */
  final public String Expression() throws ParseException {
    String innerString = "";
    String nextString = "";
        if (Thread.currentThread().isInterrupted()) {
            {if (true) throw new ParseException("CommentParser is interrupted");}
        }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case QUOTED_STRING:
      innerString = QuotedString();
      break;
    case DOUBLE_QUOTE_STRING:
      innerString = DoubleQuoteString();
      break;
    case REMAIN_TOKEN:
      innerString = RemainToken();
      break;
    case ANY:
      innerString = Any();
      break;
    default:
      jj_la1[0] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
        {if (true) return innerString + nextString;}
    throw new Error("Missing return statement in function");
  }

  final public String RemainToken() throws ParseException {
    jj_consume_token(REMAIN_TOKEN);
        logger.trace("meet token <REMAIN_TOKEN>");
        {if (true) return getToken(0).image;}
    throw new Error("Missing return statement in function");
  }

  final public String QuotedString() throws ParseException {
    String s;
    jj_consume_token(QUOTED_STRING);
        logger.trace("meet token in <QUOTED_STRING>: " + getToken(0).image);
        {if (true) return getToken(0).image;}
    throw new Error("Missing return statement in function");
  }

  final public String DoubleQuoteString() throws ParseException {
    String s;
    jj_consume_token(DOUBLE_QUOTE_STRING);
        logger.trace("meet token in <QUOTED2_STRING>: " + getToken(0).image);
        {if (true) return getToken(0).image;}
    throw new Error("Missing return statement in function");
  }

  final public String Any() throws ParseException {
    jj_consume_token(ANY);
        logger.trace("meet token in <ANY>: " + getToken(0).image);
        {if (true) return getToken(0).image;}
    throw new Error("Missing return statement in function");
  }

  private boolean jj_2_1(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_1(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(0, xla); }
  }

  private boolean jj_3R_6() {
    if (jj_3R_10()) return true;
    return false;
  }

  private boolean jj_3R_5() {
    if (jj_3R_9()) return true;
    return false;
  }

  private boolean jj_3R_4() {
    if (jj_3R_8()) return true;
    return false;
  }

  private boolean jj_3R_7() {
    if (jj_scan_token(QUOTED_STRING)) return true;
    return false;
  }

  private boolean jj_3R_10() {
    if (jj_scan_token(ANY)) return true;
    return false;
  }

  private boolean jj_3R_3() {
    if (jj_3R_7()) return true;
    return false;
  }

  private boolean jj_3_1() {
    if (jj_3R_2()) return true;
    return false;
  }

  private boolean jj_3R_2() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_3()) {
    jj_scanpos = xsp;
    if (jj_3R_4()) {
    jj_scanpos = xsp;
    if (jj_3R_5()) {
    jj_scanpos = xsp;
    if (jj_3R_6()) return true;
    }
    }
    }
    return false;
  }

  private boolean jj_3R_8() {
    if (jj_scan_token(DOUBLE_QUOTE_STRING)) return true;
    return false;
  }

  private boolean jj_3R_9() {
    if (jj_scan_token(REMAIN_TOKEN)) return true;
    return false;
  }

  /** Generated Token Manager. */
  public CommentParserTokenManager token_source;
  SimpleCharStream jj_input_stream;
  /** Current token. */
  public Token token;
  /** Next token. */
  public Token jj_nt;
  private int jj_ntk;
  private Token jj_scanpos, jj_lastpos;
  private int jj_la;
  private int jj_gen;
  final private int[] jj_la1 = new int[1];
  static private int[] jj_la1_0;
  static {
      jj_la1_init_0();
   }
   private static void jj_la1_init_0() {
      jj_la1_0 = new int[] {0x1a8,};
   }
  final private JJCalls[] jj_2_rtns = new JJCalls[1];
  private boolean jj_rescan = false;
  private int jj_gc = 0;

  /** Constructor with InputStream. */
  public CommentParser(java.io.InputStream stream) {
     this(stream, null);
  }
  /** Constructor with InputStream and supplied encoding */
  public CommentParser(java.io.InputStream stream, String encoding) {
    try { jj_input_stream = new SimpleCharStream(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source = new CommentParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 1; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Reinitialise. */
  public void ReInit(java.io.InputStream stream) {
     ReInit(stream, null);
  }
  /** Reinitialise. */
  public void ReInit(java.io.InputStream stream, String encoding) {
    try { jj_input_stream.ReInit(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 1; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Constructor. */
  public CommentParser(java.io.Reader stream) {
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new CommentParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 1; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Reinitialise. */
  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 1; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Constructor with generated Token Manager. */
  public CommentParser(CommentParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 1; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Reinitialise. */
  public void ReInit(CommentParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 1; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      if (++jj_gc > 100) {
        jj_gc = 0;
        for (int i = 0; i < jj_2_rtns.length; i++) {
          JJCalls c = jj_2_rtns[i];
          while (c != null) {
            if (c.gen < jj_gen) c.first = null;
            c = c.next;
          }
        }
      }
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  static private final class LookaheadSuccess extends java.lang.Error { }
  final private LookaheadSuccess jj_ls = new LookaheadSuccess();
  private boolean jj_scan_token(int kind) {
    if (jj_scanpos == jj_lastpos) {
      jj_la--;
      if (jj_scanpos.next == null) {
        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();
      } else {
        jj_lastpos = jj_scanpos = jj_scanpos.next;
      }
    } else {
      jj_scanpos = jj_scanpos.next;
    }
    if (jj_rescan) {
      int i = 0; Token tok = token;
      while (tok != null && tok != jj_scanpos) { i++; tok = tok.next; }
      if (tok != null) jj_add_error_token(kind, i);
    }
    if (jj_scanpos.kind != kind) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) throw jj_ls;
    return false;
  }


/** Get the next Token. */
  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

/** Get the specific Token. */
  final public Token getToken(int index) {
    Token t = token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.List<int[]> jj_expentries = new java.util.ArrayList<int[]>();
  private int[] jj_expentry;
  private int jj_kind = -1;
  private int[] jj_lasttokens = new int[100];
  private int jj_endpos;

  private void jj_add_error_token(int kind, int pos) {
    if (pos >= 100) return;
    if (pos == jj_endpos + 1) {
      jj_lasttokens[jj_endpos++] = kind;
    } else if (jj_endpos != 0) {
      jj_expentry = new int[jj_endpos];
      for (int i = 0; i < jj_endpos; i++) {
        jj_expentry[i] = jj_lasttokens[i];
      }
      jj_entries_loop: for (java.util.Iterator<?> it = jj_expentries.iterator(); it.hasNext();) {
        int[] oldentry = (int[])(it.next());
        if (oldentry.length == jj_expentry.length) {
          for (int i = 0; i < jj_expentry.length; i++) {
            if (oldentry[i] != jj_expentry[i]) {
              continue jj_entries_loop;
            }
          }
          jj_expentries.add(jj_expentry);
          break jj_entries_loop;
        }
      }
      if (pos != 0) jj_lasttokens[(jj_endpos = pos) - 1] = kind;
    }
  }

  /** Generate ParseException. */
  public ParseException generateParseException() {
    jj_expentries.clear();
    boolean[] la1tokens = new boolean[11];
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 1; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 11; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.add(jj_expentry);
      }
    }
    jj_endpos = 0;
    jj_rescan_token();
    jj_add_error_token(0, 0);
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = jj_expentries.get(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  /** Enable tracing. */
  final public void enable_tracing() {
  }

  /** Disable tracing. */
  final public void disable_tracing() {
  }

  private void jj_rescan_token() {
    jj_rescan = true;
    for (int i = 0; i < 1; i++) {
    try {
      JJCalls p = jj_2_rtns[i];
      do {
        if (p.gen > jj_gen) {
          jj_la = p.arg; jj_lastpos = jj_scanpos = p.first;
          switch (i) {
            case 0: jj_3_1(); break;
          }
        }
        p = p.next;
      } while (p != null);
      } catch(LookaheadSuccess ls) { }
    }
    jj_rescan = false;
  }

  private void jj_save(int index, int xla) {
    JJCalls p = jj_2_rtns[index];
    while (p.gen > jj_gen) {
      if (p.next == null) { p = p.next = new JJCalls(); break; }
      p = p.next;
    }
    p.gen = jj_gen + xla - jj_la; p.first = token; p.arg = xla;
  }

  static final class JJCalls {
    int gen;
    Token first;
    int arg;
    JJCalls next;
  }

}
