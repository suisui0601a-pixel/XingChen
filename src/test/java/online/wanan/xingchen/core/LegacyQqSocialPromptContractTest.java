package online.wanan.xingchen.core;

import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.context.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.prompt.QqSocialProtocol;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class LegacyQqSocialPromptContractTest {
    private ContextBuildInput input(Platform platform, ConversationType type, List<String> history) {
        var channel = new ConversationIdentity(platform, type, "synthetic-target");
        return new ContextBuildInput("active simulation fixture", "active persona fixture", "qq:synthetic-sender",
                "Synthetic Sender", "friend", channel.platformConversationId(), List.of(), "", history,
                Map.of(), "current fixture question", "", null, List.of(), channel);
    }

    @Test void actualRequestKeepsHardPolicyAndActiveLayersWithOneLegacyBehaviorSection() {
        var context = new ContextBuilder(ContextBudget.defaults()).build(input(Platform.QQ, ConversationType.GROUP, List.of("earlier fixture")));
        var request = new ModelRequest(context, AgentToolCatalog.all());
        assertThat(request.messages().getFirst().content()).isEqualTo(HardSecurityPolicy.TEXT);
        String system = request.messages().get(1).content();
        assertThat(system.split(java.util.regex.Pattern.quote("【QQ 社交底层规则】"), -1)).hasSize(2);
        assertThat(system).contains("active persona fixture", "active simulation fixture", "current MESSAGE SENDER, not the assistant",
                "conversationType=GROUP", "currentTargetKind=group", "【群聊不是客服队列】", "【保持主体性与聊天节奏】");
        assertThat(request.messages().getLast().content()).isEqualTo("current fixture question");
        assertThat(request.toolDefinitions()).extracting(ModelToolDefinition::name).contains("qq.readRecent", "memory.remember", "sticker.send");
    }

    @Test void adaptedProtocolRetainsFinalReplyAndUncertainSendBoundariesWithoutOldBridgeInstructions() {
        assertThat(QqSocialProtocol.TEXT).contains("由 Runtime 自动发送", "读工具本身没有替你发言", "返回恰好 NO_REPLY",
                "UNKNOWN 表示结果不明，绝不自动重试", "群聊不能读取或转述 PRIVATE", "没有成功写入，不声称", "服务器检查");
        assertThat(QqSocialProtocol.TEXT).doesNotContain("You are a coding agent", "{{cwd}}", "{{model}}",
                "mcp__snowluma__", "qq_get_prompt", "qq_send_message", "文本输出只是思考", "不会自动发送到 QQ",
                "preSleepWaitRemainingMs", "timeoutMs=300000", "DeepSeek 小鲸鱼");
    }

    @Test void sharedLayerDoesNotReplacePrivateContextOrApplyToOtherPlatforms() {
        var builder = new ContextBuilder(ContextBudget.defaults());
        var direct = builder.build(input(Platform.QQ, ConversationType.PRIVATE, List.of()));
        String system = new ModelRequest(direct, List.of()).messages().get(1).content();
        assertThat(system).contains("conversationType=PRIVATE", "Private one-to-one conversation", "【QQ 社交底层规则】");
        var other = builder.build(input(Platform.DISCORD, ConversationType.GROUP, List.of()));
        assertThat(new ModelRequest(other, List.of()).messages().get(1).content()).doesNotContain("【QQ 社交底层规则】");
        var legacy = builder.build("p", "s", "a", "c", "", List.of(), List.of(), Map.of(), "m", 0, null);
        assertThat(new ModelRequest(legacy, List.of()).messages().get(1).content()).doesNotContain("【QQ 社交底层规则】");
    }

    @Test void fixedProtocolCostIsBudgetedBeforeHistoryAndSurvivesToolContextRebuild() {
        var estimator = new ApproximateTokenEstimator();
        var ample = new ContextBuilder(ContextBudget.defaults(), estimator);
        var baseline = ample.build(input(Platform.QQ, ConversationType.GROUP, List.of()));
        int limit = baseline.estimatedTokens() + 100;
        var bounded = new ContextBuilder(new ContextBudget(limit - 2, limit - 1, limit, 40, 0, 0, 50, 20), estimator);
        var context = bounded.build(input(Platform.QQ, ConversationType.GROUP, List.of("large historical fixture ".repeat(1000))));
        assertThat(context.recentMessages()).isEmpty();
        assertThat(context.estimatedTokens()).isLessThanOrEqualTo(limit);
        assertThat(context.diagnostics().calibratedTokens()).isGreaterThanOrEqualTo(estimator.estimate(QqSocialProtocol.TEXT));
        var rebuilt = bounded.build(new ContextBuildInput(context.simulationPrompt(), context.persona(), context.actorId(),
                context.displayName(), context.relationshipAddress(), context.conversationId(), context.memories(), "",
                context.recentMessages(), context.taskState(), context.currentMessage(), context.toolDescription(), null,
                List.of("read-only fixture result"), context.conversationIdentity(), context.historyMessages()));
        assertThat(new ModelRequest(rebuilt, List.of()).messages().get(1).content()).contains(QqSocialProtocol.TEXT);
        assertThat(rebuilt.estimatedTokens()).isLessThanOrEqualTo(limit);
    }
}
