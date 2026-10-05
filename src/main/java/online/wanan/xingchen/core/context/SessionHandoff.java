package online.wanan.xingchen.core.context;

import java.util.List;

public record SessionHandoff(String conversationSummary, List<String> activeTopics, List<String> people,
                             List<String> relationships, List<String> openTasks, List<String> decisions,
                             List<String> importantFacts, List<String> recentContext) {}
