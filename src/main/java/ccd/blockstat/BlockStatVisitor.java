package ccd.blockstat;

import antlr.Java8BaseVisitor;
import antlr.Java8Parser;
import ccd.PropsLoader;
import ccd.linenode.RuleFilterForLine;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import redis.clients.jedis.Jedis;

import java.util.Stack;

public class BlockStatVisitor extends Java8BaseVisitor<Integer> {
    private static int methodLimitedLine = Integer.valueOf(PropsLoader.getProperty("ccd.methodLimitedLine"));
    private final String ccdSeparate = "ccdMethodSeparate";
    private int startLine;
    private String pathFilename;//方法所在文件的路径以及文件名
    private Jedis redis;

    @Override
    public Integer visitMethodHeader(Java8Parser.MethodHeaderContext ctx) {
        startLine = ctx.start.getLine();
        return visitChildren(ctx);
    }
    @Override
    public Integer visitMethodBody(Java8Parser.MethodBodyContext ctx) {
        //将AST转化成network形式:
        int stopLine = ctx.stop.getLine();
        if((stopLine - startLine + 1) < methodLimitedLine)//方法代码行过滤阈值, 低于阈值的不参与检测
            return null;
        String key = pathFilename + "(" + startLine + "-" + stopLine + ")";
        Stack<ParseTree> stack = new Stack<>();
        stack.push(ctx);
        int ruleIndex;
        StringBuilder statement = new StringBuilder();
        StringBuilder line = new StringBuilder();
        int beforeLine = -1;
        while(!stack.empty()){
            ParseTree node = stack.pop();
            //beta优化: 按代码行序列相似求beta
            if(node instanceof RuleNode){
                int nodeIndex = ((RuleNode) node).getRuleContext().getRuleIndex();
                if (RuleFilterForLine.ruleFilter().containsKey(nodeIndex)) {
                    ParserRuleContext ct = (ParserRuleContext)node;
                    int currentLine = ct.getStart().getLine();
                    if(beforeLine == -1){
                        beforeLine = currentLine;
                    }
                    if(currentLine != beforeLine){
                        line.deleteCharAt(line.length()-1);
                        line.append(";").append(nodeIndex+",");
                        beforeLine = currentLine;
                    }else {
                        line.append(nodeIndex+",");
                    }
                }
            }
            if(node instanceof RuleNode){
                ruleIndex = ((RuleNode) node).getRuleContext().getRuleIndex();
                if(ruleIndex == Java8Parser.RULE_blockStatement){
                    ParserRuleContext ct = (ParserRuleContext)node;
                    Stack<ParseTree> blockStack = new Stack<>();
                    blockStack.push(ct);
                    int blockRuleIndex;
                    while (!blockStack.empty()) {
                        ParseTree blockNode = blockStack.pop();
                        if (blockNode instanceof RuleNode) {
                            blockRuleIndex = ((RuleNode) blockNode).getRuleContext().getRuleIndex();
                            if (RuleFilter.ruleFilter().containsKey(blockRuleIndex)) {
                                statement.append(blockRuleIndex+",");
                            }
                            for (int len = blockNode.getChildCount(), i = len - 1; i >= 0; i--) {
                                blockStack.push(blockNode.getChild(i));
                            }
                        }
                    }
                    statement.deleteCharAt(statement.length()-1);
                    statement.append(";");
                }
                for(int len = node.getChildCount(), i = len - 1; i >= 0; i --){
                    stack.push(node.getChild(i));
                }
            }
        }

        //method statements
        statement.deleteCharAt(statement.length()-1);
        //method line
        line.deleteCharAt(line.length()-1).append(";");
//        System.out.println(statement.toString());
        String[] show = statement.toString().split(";");
        for (int i = 0; i < show.length; i++) {
            System.out.println((i+1)+": "+show[i]);
        }
//        System.out.println(line.toString());

        String value = line.toString() +ccdSeparate+ statement.toString();
        redis.set(key, value);
//        System.out.println("value: "+value);
        System.out.println("=================================");
        return visitChildren(ctx);
    }

    public void setPathFilename(String pathFilename) {
        this.pathFilename = pathFilename;
    }
    public void setRedis(Jedis redis) {
        this.redis = redis;
    }
}
