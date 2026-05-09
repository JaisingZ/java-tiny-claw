package com.jaising.agent.app;

/**
 * 应用入口
 * 只负责启动提示 不承载业务逻辑
 */
public final class AgentApplication {

  private AgentApplication() {
  }

  /*
   * 程序入口
   * 当前版本仅输出启动信息
   */
  public static void main(String[] args) {
    System.out.println("java-tiny-claw agent harness");
  }
}
