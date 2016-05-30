package io.burt.jmespath;

import java.util.Deque;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

import io.burt.jmespath.Query;
import io.burt.jmespath.node.AndNode;
import io.burt.jmespath.node.ComparisonNode;
import io.burt.jmespath.node.CreateArrayNode;
import io.burt.jmespath.node.CreateObjectNode;
import io.burt.jmespath.node.CurrentNode;
import io.burt.jmespath.node.ExpressionReferenceNode;
import io.burt.jmespath.node.FlattenArrayNode;
import io.burt.jmespath.node.FlattenObjectNode;
import io.burt.jmespath.node.ForkNode;
import io.burt.jmespath.node.FunctionCallNode;
import io.burt.jmespath.node.IndexNode;
import io.burt.jmespath.node.JmesPathNode;
import io.burt.jmespath.node.JoinNode;
import io.burt.jmespath.node.JsonLiteralNode;
import io.burt.jmespath.node.NegateNode;
import io.burt.jmespath.node.OrNode;
import io.burt.jmespath.node.PropertyNode;
import io.burt.jmespath.node.SelectionNode;
import io.burt.jmespath.node.SliceNode;
import io.burt.jmespath.node.StringNode;

public class AstGeneratingVisitor extends JmesPathBaseVisitor<JmesPathNode> {
  private final ParseTree tree;
  private final Deque<JmesPathNode> currentSource;
  private final JsonGeneratingVisitor jsonGenerator;

  public AstGeneratingVisitor(ParseTree tree) {
    this.tree = tree;
    this.currentSource = new LinkedList<>();
    this.jsonGenerator = new JsonGeneratingVisitor();
  }

  public Query query() {
    return new Query(visit(tree));
  }

  private String identifierToString(JmesPathParser.IdentifierContext ctx) {
    String id = ctx.getText();
    if (ctx.STRING() != null) {
      id = id.substring(1, id.length() - 1);
    }
    return id;
  }

  @Override
  public JmesPathNode visitQuery(JmesPathParser.QueryContext ctx) {
    currentSource.push(new CurrentNode());
    JmesPathNode result = visit(ctx.expression());
    currentSource.pop();
    return result;
  }

  @Override
  public JmesPathNode visitPipeExpression(JmesPathParser.PipeExpressionContext ctx) {
    currentSource.push(new JoinNode(visit(ctx.expression(0))));
    JmesPathNode result = visit(ctx.expression(1));
    currentSource.pop();
    return result;
  }

  @Override
  public JmesPathNode visitIdentifierExpression(JmesPathParser.IdentifierExpressionContext ctx) {
    return visit(ctx.identifier());
  }

  @Override
  public JmesPathNode visitNotExpression(JmesPathParser.NotExpressionContext ctx) {
    return new NegateNode(visit(ctx.expression()));
  }

  @Override
  public JmesPathNode visitRawStringExpression(JmesPathParser.RawStringExpressionContext ctx) {
    String quotedString = ctx.RAW_STRING().getText();
    String unquotedString = quotedString.substring(1, quotedString.length() - 1);
    return new StringNode(unquotedString);
  }

  @Override
  public JmesPathNode visitComparisonExpression(JmesPathParser.ComparisonExpressionContext ctx) {
    String operator = ctx.COMPARATOR().getText();
    JmesPathNode left = visit(ctx.expression(0));
    JmesPathNode right = visit(ctx.expression(1));
    return new ComparisonNode(operator, left, right);
  }

  @Override
  public JmesPathNode visitParenExpression(JmesPathParser.ParenExpressionContext ctx) {
    return visit(ctx.expression());
  }

  @Override
  public JmesPathNode visitBracketExpression(JmesPathParser.BracketExpressionContext ctx) {
    return visit(ctx.bracketSpecifier());
  }

  @Override
  public JmesPathNode visitOrExpression(JmesPathParser.OrExpressionContext ctx) {
    JmesPathNode left = visit(ctx.expression(0));
    JmesPathNode right = visit(ctx.expression(1));
    return new OrNode(left, right);

  }

  @Override
  public JmesPathNode visitChainExpression(JmesPathParser.ChainExpressionContext ctx) {
    currentSource.push(visit(ctx.expression()));
    JmesPathNode result = visit(ctx.chainedExpression());
    currentSource.pop();
    return result;
  }

  @Override
  public JmesPathNode visitAndExpression(JmesPathParser.AndExpressionContext ctx) {
    JmesPathNode left = visit(ctx.expression(0));
    JmesPathNode right = visit(ctx.expression(1));
    return new AndNode(left, right);
  }

  @Override
  public JmesPathNode visitWildcardExpression(JmesPathParser.WildcardExpressionContext ctx) {
    return visit(ctx.wildcard());
  }

  @Override
  public JmesPathNode visitBracketedExpression(JmesPathParser.BracketedExpressionContext ctx) {
    currentSource.push(visit(ctx.expression()));
    JmesPathNode result = visit(ctx.bracketSpecifier());
    currentSource.pop();
    return result;
  }

  @Override
  public JmesPathNode visitWildcard(JmesPathParser.WildcardContext ctx) {
    return new ForkNode(new FlattenObjectNode(currentSource.peek()));
  }

  @Override
  public JmesPathNode visitMultiSelectList(JmesPathParser.MultiSelectListContext ctx) {
    currentSource.push(new CurrentNode());
    int n = ctx.expression().size();
    JmesPathNode[] entries = new JmesPathNode[n];
    for (int i = 0; i < n; i++) {
      entries[i] = visit(ctx.expression(i));
    }
    currentSource.pop();
    return new CreateArrayNode(entries, currentSource.peek());
  }

  @Override
  public JmesPathNode visitMultiSelectHash(JmesPathParser.MultiSelectHashContext ctx) {
    currentSource.push(new CurrentNode());
    int n = ctx.keyvalExpr().size();
    CreateObjectNode.Entry[] entries = new CreateObjectNode.Entry[n];
    for (int i = 0; i < n; i++) {
      JmesPathParser.KeyvalExprContext kvCtx = ctx.keyvalExpr(i);
      String key = identifierToString(kvCtx.identifier());
      JmesPathNode value = visit(kvCtx.expression());
      entries[i] = new CreateObjectNode.Entry(key, value);
    }
    currentSource.pop();
    return new CreateObjectNode(entries, currentSource.peek());
  }

  @Override
  public JmesPathNode visitBracketIndex(JmesPathParser.BracketIndexContext ctx) {
    int index = Integer.parseInt(ctx.SIGNED_INT().getText());
    return new IndexNode(index, currentSource.peek());
  }

  @Override
  public JmesPathNode visitBracketStar(JmesPathParser.BracketStarContext ctx) {
    return new ForkNode(currentSource.peek());
  }

  @Override
  public JmesPathNode visitBracketSlice(JmesPathParser.BracketSliceContext ctx) {
    int start = 0;
    int stop = 0;
    int step = 1;
    JmesPathParser.SliceContext sliceCtx = ctx.slice();
    if (sliceCtx.start != null) {
      start = Integer.parseInt(sliceCtx.start.getText());
    }
    if (sliceCtx.stop != null) {
      stop = Integer.parseInt(sliceCtx.stop.getText());
    }
    if (sliceCtx.step != null) {
      step = Integer.parseInt(sliceCtx.step.getText());
    }
    return new SliceNode(start, stop, step, currentSource.peek());
  }

  @Override
  public JmesPathNode visitBracketFlatten(JmesPathParser.BracketFlattenContext ctx) {
    return new ForkNode(new FlattenArrayNode(currentSource.peek()));
  }

  @Override
  public JmesPathNode visitSelect(JmesPathParser.SelectContext ctx) {
    currentSource.push(new CurrentNode());
    JmesPathNode test = visit(ctx.expression());
    currentSource.pop();
    return new ForkNode(new SelectionNode(test, currentSource.peek()));
  }

  @Override
  public JmesPathNode visitFunctionExpression(JmesPathParser.FunctionExpressionContext ctx) {
    currentSource.push(new CurrentNode());
    String name = ctx.NAME().getText();
    int n = ctx.functionArg().size();
    JmesPathNode[] args = new JmesPathNode[n];
    for (int i = 0; i < n; i++) {
      args[i] = visit(ctx.functionArg(i));
    }
    currentSource.pop();
    return new FunctionCallNode(name, args, currentSource.peek());
  }

  @Override
  public JmesPathNode visitCurrentNode(JmesPathParser.CurrentNodeContext ctx) {
    if (currentSource.peek() instanceof CurrentNode) {
      return currentSource.peek();
    } else {
      return new CurrentNode(currentSource.peek());
    }
  }

  @Override
  public JmesPathNode visitExpressionType(JmesPathParser.ExpressionTypeContext ctx) {
    return new ExpressionReferenceNode(visit(ctx.expression()));
  }

  @Override
  public JmesPathNode visitLiteral(JmesPathParser.LiteralContext ctx) {
    String string = ctx.jsonValue().getText();
    Object tree = jsonGenerator.visit(ctx);
    return new JsonLiteralNode(string, tree);
  }

  @Override
  public JmesPathNode visitIdentifier(JmesPathParser.IdentifierContext ctx) {
    return new PropertyNode(identifierToString(ctx), currentSource.peek());
  }

  private static class JsonGeneratingVisitor extends JmesPathBaseVisitor<Object> {
    @Override
    public Object visitLiteral(JmesPathParser.LiteralContext ctx) {
      return visit(ctx.jsonValue());
    }

    @Override
    public Object visitJsonObject(JmesPathParser.JsonObjectContext ctx) {
      Map<String, Object> object = new LinkedHashMap<>();
      for (final JmesPathParser.JsonObjectPairContext pair : ctx.jsonObjectPair()) {
        String key = pair.STRING().getText();
        Object value = visit(pair.jsonValue());
      }
      return object;
    }

    @Override
    public Object visitJsonArray(JmesPathParser.JsonArrayContext ctx) {
      List<Object> array = new ArrayList(ctx.jsonValue().size());
      for (final JmesPathParser.JsonValueContext entry : ctx.jsonValue()) {
        array.add(visit(entry));
      }
      return array;
    }

    @Override
    public Object visitJsonStringValue(JmesPathParser.JsonStringValueContext ctx) {
      String string = ctx.getText();
      string = string.substring(1, string.length() - 1);
      return string;
    }

    @Override
    public Object visitJsonNumberValue(JmesPathParser.JsonNumberValueContext ctx) {
      return Double.parseDouble(ctx.getText());
    }

    @Override
    public Object visitJsonObjectValue(JmesPathParser.JsonObjectValueContext ctx) {
      return visit(ctx.jsonObject());
    }

    @Override
    public Object visitJsonArrayValue(JmesPathParser.JsonArrayValueContext ctx) {
      return visit(ctx.jsonArray());
    }

    @Override
    public Object visitJsonConstantValue(JmesPathParser.JsonConstantValueContext ctx) {
      if (ctx.t != null) {
        return true;
      } else if (ctx.f != null) {
        return false;
      } else {
        return null;
      }
    }
  }
}
