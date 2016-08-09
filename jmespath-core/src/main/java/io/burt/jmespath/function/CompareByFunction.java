package io.burt.jmespath.function;

import java.util.Iterator;
import java.util.List;

import io.burt.jmespath.JmesPathRuntime;
import io.burt.jmespath.JmesPathType;
import io.burt.jmespath.node.JmesPathNode;

public abstract class CompareByFunction extends JmesPathFunction {
  public CompareByFunction() {
    super(
      ArgumentConstraints.arrayOf(ArgumentConstraints.typeOf(JmesPathType.OBJECT)),
      ArgumentConstraints.expression()
    );
  }

  protected abstract boolean sortsBefore(int compareResult);

  @Override
  protected <T> T callFunction(JmesPathRuntime<T> runtime, List<ExpressionOrValue<T>> arguments) {
    Iterator<T> elements = runtime.toList(arguments.get(0).value()).iterator();
    JmesPathNode expression = arguments.get(1).expression();
    if (elements.hasNext()) {
      T result = elements.next();
      T resultValue = expression.evaluate(runtime, result);
      boolean expectNumbers = true;
      if (runtime.typeOf(resultValue) == JmesPathType.STRING) {
        expectNumbers = false;
      } else if (runtime.typeOf(resultValue) != JmesPathType.NUMBER) {
        throw new ArgumentTypeException(name(), "number or string", runtime.typeOf(resultValue).toString());
      }
      while (elements.hasNext()) {
        T candidate = elements.next();
        T candidateValue = expression.evaluate(runtime, candidate);
        JmesPathType candidateType = runtime.typeOf(candidateValue);
        if (expectNumbers && candidateType != JmesPathType.NUMBER) {
          throw new ArgumentTypeException(name(), "number", candidateType.toString());
        } else if (!expectNumbers && candidateType != JmesPathType.STRING) {
          throw new ArgumentTypeException(name(), "string", candidateType.toString());
        }
        if (sortsBefore(runtime.compare(candidateValue, resultValue))) {
          result = candidate;
          resultValue = candidateValue;
        }
      }
      return result;
    } else {
      return runtime.createNull();
    }
  }
}
