package wasm;

import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Tokenizer {
    public enum TokenType {
        number,
        keyword,
        whitespace,
        parens,
        parensClose,
        operator,
        identifier,
        assignment;
    }
    public class Token {
        public TokenType type;
        public String value;
        public int line;
        public int character;
        public Token(TokenType type, String value, int line, int character) {
            this.type = type;
            this.value = value;
            this.line = line;
            this.character = character;
        }
    }
    public static final String[] keywords = {"print", "var", "while", "endwhile", "setpixel", "if", "endif", "else"};
    public static final String[] operators = {"+", "-", "*", "/", "==", "<", ">", "&&"};

    private List<BiFunction<String, Integer, Optional<Token>>> matchers = new ArrayList<>();
    public Tokenizer() {
        matchers.add(regexMatcher("^-?[.0-9]+([eE]-?[0-9]{2})?", TokenType.number));

        matchers.add(regexMatcher("^(" + Arrays.stream(keywords).collect(Collectors.joining("|")) + ")", TokenType.keyword));
        matchers.add(regexMatcher("^\\s+", TokenType.whitespace));

        //should be something like List<String> escapedOperators = Arrays.stream(operators).map(str -> str.replace("[-[\]{}()*+?.,\\^$|#\s]", "\\$&")).collect(Collectors.toList());
        String escapedOperators = Arrays.stream(operators).map(str -> Pattern.quote(str)).collect(Collectors.joining("|"));
        matchers.add(regexMatcher("^(" + escapedOperators + ")", TokenType.operator));
        matchers.add(regexMatcher("^[a-zA-Z]+", TokenType.identifier));

        matchers.add(regexMatcher("^=", TokenType.assignment));
        matchers.add(regexMatcher("\\([^\\[]*", TokenType.parens));
        matchers.add(regexMatcher("[^\\[]*\\)", TokenType.parensClose));
    }
    private BiFunction<String, Integer, Optional<Token>> regexMatcher(String regex, TokenType type) {
        BiFunction<String, Integer, Optional<Token>> func = (input, index) -> {
            String substr = input.substring(index);
            int spaceIndex = substr.indexOf(" ");
            if(spaceIndex > -1) {
                substr = substr.substring(0, spaceIndex);
            }
            StringTokenizer st = new StringTokenizer(substr, " ");
            if(! st.hasMoreTokens()) {
                return Optional.empty();
            }
            String token = st.nextToken();
            try {
                if (token.matches(regex)) {
                    return Optional.of(new Token(type, substr, 0, index));
                } else {
                    return Optional.empty();
                }
            }catch(Exception e){
                throw new IllegalArgumentException(e);
            }
        };
        return func;
    }
    public List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while(index < input.length()) {
            final int indexParam = index;
            List<Token> found = matchers.stream().map(m -> m.apply(input, indexParam))
                    .filter(res -> res.isPresent())
                    .map(opt -> opt.get())
                    .collect(Collectors.toList());
            if(found.size() > 0) {
                Token token = found.get(0);
                if(token.type != TokenType.whitespace && token.type != TokenType.parensClose) {
                    tokens.add(token);
                }
                index = index + token.value.length() +1;
            }
        }
        return tokens;
    }
}
