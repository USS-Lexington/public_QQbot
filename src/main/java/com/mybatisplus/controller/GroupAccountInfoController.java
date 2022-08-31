package com.mybatisplus.controller;


import catcode.CatCodeUtil;
import catcode.Neko;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mybatisplus.entity.Group_And_Sender;
import com.mybatisplus.entity.Message;
import com.mybatisplus.listener.MyNewGroupMemberListen;
import com.mybatisplus.service.IAdminService;
import com.mybatisplus.service.IMessageService;
import com.mybatisplus.utils.GetNews;
import com.mybatisplus.utils.HistoryTody;
import com.mybatisplus.utils.Random_say;
import love.forte.common.ioc.annotation.Depend;
import love.forte.simbot.annotation.*;
import love.forte.simbot.api.message.MessageContent;
import love.forte.simbot.api.message.MessageContentBuilder;
import love.forte.simbot.api.message.MessageContentBuilderFactory;
import love.forte.simbot.api.message.containers.AccountInfo;
import love.forte.simbot.api.message.containers.GroupAccountInfo;
import love.forte.simbot.api.message.containers.GroupInfo;
import love.forte.simbot.api.message.events.GroupMemberIncrease;
import love.forte.simbot.api.message.events.GroupMsg;

import love.forte.simbot.api.message.events.PrivateMsg;
import love.forte.simbot.api.sender.MsgSender;
import love.forte.simbot.api.sender.Sender;
import love.forte.simbot.component.mirai.message.MiraiMessageContent;
import love.forte.simbot.component.mirai.message.MiraiMessageContentBuilder;
import love.forte.simbot.component.mirai.message.MiraiMessageContentBuilderFactory;
import love.forte.simbot.filter.MatchType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author wxt
 * @since 2022-07-23
 *   @OnGroup
 *     public void onGroupMsg(GroupMsg groupMsg) {
 *         // 打印此次消息中的 纯文本消息内容。
 *         // 纯文本消息中，不会包含任何特殊消息（例如图片、表情等）。
 *         System.out.println(groupMsg.getText());
 *
 *         // 打印此次消息中的 消息内容。
 *         // 消息内容会包含所有的消息内容，也包括特殊消息。特殊消息使用CAT码进行表示。
 *         // 需要注意的是，绝大多数情况下，getMsg() 的效率低于甚至远低于 getText()
 *         System.out.println(groupMsg.getMsg());
 *
 *         // 获取此次消息中的 消息主体。
 *         // messageContent代表消息主体，其中通过可以获得 msg, 以及特殊消息列表。
 *         // 特殊消息列表为 List<Neko>, 其中，Neko是CAT码的封装类型。
 *
 *         MessageContent msgContent = groupMsg.getMsgContent();
 *
 *         // 打印消息主体
 *         System.out.println(msgContent);
 *         // 打印消息主体中的所有图片的链接（如果有的话）
 *         List<Neko> imageCats = msgContent.getCats("image");
 *         System.out.println("img counts: " + imageCats.size());
 *         for (Neko image : imageCats) {
 *             System.out.println("Img url: " + image.get("url"));
 *         }
 *
 *
 *         // 获取发消息的人。
 *         GroupAccountInfo accountInfo = groupMsg.getAccountInfo();
 *         // 打印发消息者的账号与昵称。
 *         System.out.println(accountInfo.getAccountCode());
 *         System.out.println(accountInfo.getAccountNickname());
 *
 *
 *         // 获取群信息
 *         GroupInfo groupInfo = groupMsg.getGroupInfo();
 *         // 打印群号与名称
 *         System.out.println(groupInfo.getGroupCode());
 *         System.out.println(groupInfo.getGroupName());
 *     }
 */
@Controller

public class GroupAccountInfoController {
    @Autowired
private IMessageService service;

    private Map map=new HashMap<GroupMsg,Sender>();

    private HashSet<Group_And_Sender> set=new HashSet();
    private Group_And_Sender group_and_sender = null;


    private volatile boolean send_flag=true; //回复模块启动标志

    private volatile boolean ds_flag=true; //定时模块启动标志



    /**
     * 用来缓存入群申请的时候所填的信息。
     */
    private static final Map<String, String> REQUEST_TEXT_MAP = new ConcurrentHashMap<>();

    /**
     * logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MyNewGroupMemberListen.class);


    @Autowired
    private MessageContentBuilderFactory factory;

    @Autowired
    private IAdminService adminService;
@Autowired
private Random_say random_say;

    @Async
    @OnGroup
    @Filter(value="nana帮助",trim=true,matchType = MatchType.CONTAINS)
    public void help(GroupMsg groupMsg, Sender sender) {

        if (!(factory instanceof MiraiMessageContentBuilderFactory)) {
            throw new RuntimeException("不支持mirai组件");
        }
        MiraiMessageContentBuilder builder = ((MiraiMessageContentBuilderFactory) factory).getMessageContentBuilder();

        // 通过 MiraiMessageContentBuilder.forwardMessage 构建一个合并消息。
        // 一般来讲，合并消息不支持与其他类型消息同时存在，因此不应再继续拼接其他消息。
        builder.forwardMessage(forwardBuilder -> {
            forwardBuilder.add(groupMsg.getBotInfo(),
                    "目前nana有一下几个功能:\n 1.关键词触发" +
                            "\n 2.学习功能 发送 nana学习 可以触发 " +
                            "\n 学习第一次发送的是key要求key必须是字符(建议出发关键字不要过短因为查询用的是模糊查询) " +
                            "\n 第二次发送的是value value可以是图片 " +
                    "\n 3.删除功能 发送nana删除 可以触发 " +
                    "\n 4.nana图片 发送随机二次元图片(2022.8.1新增) " +
                    "\n 5.nana查询关键词 根据nana自动触发的返回值查询触发词(2022.8.2新增)"+
                    "\n 6.定时发送固定信息(2022.8.3新增)"+
                    "\n 7.新增权限管理(2022.8.3新增)"+
                    "\n 如果机器人出现bug请管理员及时禁言 "+
                    "\n 8.nana天气 新增天气查询(2022.8.15)"+
                    "\n 9.nana模块管理" +
                    "\n nana听歌  (示例:nana听歌 Hurt)"+
                    "\n 11.nana每日新闻"+
                    "\n 12.nana微博热搜"+
                    "\n 13.nana历史上的今天"+
                    "\n 14.戳一戳nana发送信息"
                    +"\n 15.nana翻译(示例: nana翻译 hello)"
                    +"\n 16.nana百度(使用方法示例: nana百度 春节)"
                    );
            forwardBuilder.add(groupMsg.getBotInfo(),"nana模块管理(管理员使用)");
            forwardBuilder.add(groupMsg.getBotInfo(),"更多功能正在开发中(指刚刚新建好文件夹)");
        });

        final MiraiMessageContent messageContent = builder.build();
        // 发送消息
        sender.sendGroupMsg(groupMsg, messageContent);
    }

    @OnGroup
    @Filter(value="nana模块管理",trim=true,matchType = MatchType.CONTAINS)
    public void helpmk(GroupMsg groupMsg, Sender sender) {
        if (!(factory instanceof MiraiMessageContentBuilderFactory)) {
            throw new RuntimeException("不支持mirai组件");
        }
        MiraiMessageContentBuilder builder = ((MiraiMessageContentBuilderFactory) factory).getMessageContentBuilder();
        builder.forwardMessage(forwardBuilder -> {
            forwardBuilder.add(groupMsg.getBotInfo(),"nana启动学习模块\n" +
                    "nana关闭学习模块\n " +
                    "nana关闭回复模块\n " +
                    "nana启动回复模块\n" +
                    "nana关闭定时模块\n" +
                    "nana启动定时模块\n" +
                    "nana关闭天气模块\n" +
                    "nana启动天气模块 (超级管理员使用)"
            );
        });

        final MiraiMessageContent messageContent = builder.build();

        // 发送消息
        sender.sendGroupMsg(groupMsg, messageContent);

    }



    @Scheduled(cron="0 30 7 * * *")
    public void send_morning(){
        if(ds_flag) {
            GroupMsg group = null;
            Sender sender = null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group, "早上起来 拥抱太阳! 今天大家也要好好吃饭 ");
                sender.sendGroupMsg(group, "生活是美好的 希望大家都能照顾好自己");
            }
        }
    }

    @Scheduled(cron="0 30 9 * * *")
    public void tg(){
        if(ds_flag) {
            GroupMsg group = null;
            Sender sender = null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group, "大家好 希望大家多喝热水 不要长时间久坐");
            }
        }
    }

    @Scheduled(cron="0 0 9 ? * MON-FRI")
    public void sb(){
        if(ds_flag) {
            GroupMsg group = null;
            Sender sender = null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group, "工作时间到了 希望大家在工作中都有一个好心情 改摸鱼就摸鱼吧 一定要记得放松一下自己哦");
            }
        }
    }
    @Scheduled(cron="0 0 10 ? * MON-FRI")
    public void sb1(){
        if(ds_flag) {
            GroupMsg group = null;
            Sender sender = null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group, "十点啦 去接个水上个厕所 走动一下吧 适当的摸鱼可以保持一天的好心情哦");
            }
        }
    }

    @Scheduled(cron="0 0 18 ? * FRI")
    public void sb2(){
        if(ds_flag) {
            GroupMsg group = null;
            Sender sender = null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group, "周五下班了 这一周工作辛苦了 周末就好好放松一下吧");
            }
        }
    }


    @Scheduled(cron="0 0 18 ? * MON-FRI")
    public void xb(){
        if(ds_flag) {
            GroupMsg group = null;
            Sender sender = null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group, "下班啦下班了 今天也好好的犒劳一下自己吧");
            }
        }
    }


    @Scheduled(cron="0 0 15 * * *")
    public void tg2(){
        if(ds_flag) {
            GroupMsg group = null;
            Sender sender = null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group, "希望大家不要久坐 多从位置上起来走走");
            }
        }
    }

    //{ 秒数} {分钟} {小时} {日期} {月份} {星期}
    @Scheduled(cron="0 30 19 * * * ")
    public void send_evening(){
        if(ds_flag) {
            GroupMsg group = null;
            Sender sender = null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group, "晚上好 今天晚上也要好好吃饭");
                sender.sendGroupMsg(group, "生活是美好的 希望大家都能照顾好自己");
            }
        }
    }

    @Scheduled(cron="0 0 12 * * * ")
    public void send_afternoon(){
        if(ds_flag){
            GroupMsg group=null;
            Sender sender =null;
            for (Group_And_Sender group_and_sender : set) {
                group = group_and_sender.getGroup();
                sender = group_and_sender.getSender();
                sender.sendGroupMsg(group,"中午好 中午记得要午休哦");
                sender.sendGroupMsg(group,"生活是美好的 希望大家都能照顾好自己");
            }
        }

    }
//==============

    @Autowired
    private HistoryTody historyTody;
    @Scheduled(cron="0 0 7 * * * ")
    public void historyTody(){
        if(ds_flag) {
            for (Group_And_Sender group_and_sender : set) {
                GroupMsg group = group_and_sender.getGroup();
                Sender sender = group_and_sender.getSender();


                MiraiMessageContentBuilder builder = ((MiraiMessageContentBuilderFactory) factory).getMessageContentBuilder();
                String historytody = historyTody.historytody();
                String finalS = historytody;
                String replace = finalS.replaceAll("\\\\", "").replaceAll(",", "").replace("[", "").replace("]", "").replaceAll("\"", "");
                String[] split = replace.split("n");
                StringBuilder sbuilder = new StringBuilder();

                GroupMsg finalGroup = group;
                builder.forwardMessage(forwardBuilder -> {
                    for (String s : split) {
                        sbuilder.append(s).append("\n");
                             }
            //        String n = s.replaceAll("n", " ");
                    forwardBuilder.add(finalGroup.getBotInfo(), String.valueOf(sbuilder));

                });
                final MiraiMessageContent messageContent = builder.build();

                // 发送消息
                sender.sendGroupMsg(group, messageContent);
            }
        }

        }


    @Autowired
    private GetNews getNews;
    @Scheduled(cron="0 0 8 * * * ")
    public void sendNews() throws IOException {
        if(ds_flag) {
            MiraiMessageContentBuilder builder = ((MiraiMessageContentBuilderFactory) factory).getMessageContentBuilder();
            String s = getNews.EveryDayNews();
            for (Group_And_Sender group_and_sender : set) {
                GroupMsg group = group_and_sender.getGroup();
                Sender sender = group_and_sender.getSender();

                String finalS = s;
                GroupMsg finalGroup = group;
                builder.forwardMessage(forwardBuilder -> {
                    forwardBuilder.add(finalGroup.getBotInfo(), finalS);
                });
                final MiraiMessageContent messageContent = builder.build();
                sender.sendGroupMsg(group, "早上好 这是今天的每日新闻 本新闻来源于知乎");
                // 发送消息
                sender.sendGroupMsg(group, messageContent);
            }
        }
    }
//====================================================

    @Async
    @OnGroup
    public void onGroupMsg(GroupMsg groupMsg,Sender sender) throws IOException {
        if (send_flag) {
            String valuemessage = "";
            String text = groupMsg.getText();//获取发生信息
            group_and_sender = new Group_And_Sender();
            group_and_sender.setSender(sender);
            group_and_sender.setGroup(groupMsg);
            set.add(group_and_sender);
            if (!text.equals("") && text != null) {
                Message message = new Message();
                message.setKeymessage(text);
                List<Message> messages = service.Get_Message_by_key(message);
                int size = messages.size();
                if (messages.size() != 0) {
                    if (messages.size() == 1) {
                        valuemessage = messages.get(0).getValuemessage();
                        if (valuemessage.equals("")) {
                            String url = "[CAT:image,file=" + messages.get(0).getUrl() + "]";
                            sender.sendGroupMsg(groupMsg, url);
                        } else {
                            sender.sendGroupMsg(groupMsg, messages.get(0).getValuemessage());
                        }

                    } else {
                        double d = Math.random();
                        int i = (int) (d * size);
                        valuemessage = messages.get(i).getValuemessage();
                        if (valuemessage.equals("")) {
                            CatCodeUtil util = CatCodeUtil.INSTANCE;
                            String url = "[CAT:image,file=" + messages.get(i).getUrl() + "]";

                            sender.sendGroupMsg(groupMsg, url);
                        } else {

                            sender.sendGroupMsg(groupMsg, valuemessage);

                        }
                    }
                }
            }



        try{
            //查询b站信息
            List<Neko> cats = groupMsg.getMsgContent().getCats();
            Neko neko = cats.get(0);
            String content = neko.get("content");
            JSONObject jsonObject = JSON.parseObject(content);
            if(!Objects.isNull(jsonObject.get("desc").toString())){
                String desc = jsonObject.get("desc").toString();
                if (desc!=null&&desc.equals("哔哩哔哩")){

                    String meta = jsonObject.get("meta").toString();
                    JSONObject json = JSON.parseObject(meta);
                    JSONObject  detail_1= (JSONObject) json.get("detail_1");
                    String url =  detail_1.get("qqdocurl").toString();

                    Document parse = null;
                    try {
                        parse = Jsoup.parse(new URL(url), 3000);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Elements elementsByClass = parse.getElementsByClass("desc-info desc-v2 open");
                    Element comment = parse.getElementById("v_desc");
                    sender.sendGroupMsg(groupMsg,"视频的简介是:\n"+comment.text());
                }
            }
        }catch (Exception e){

        }
        MessageContent messageContent=groupMsg.getMsgContent();
            List<Neko> cats = messageContent.getCats();
            if(cats!=null){
                Neko neko = cats.get(0);
                String botCode = groupMsg.getBotInfo().getBotCode();
                if(neko!=null&&neko.getType().equals("nudge")&&neko.get("target").equals(botCode)){
                    sender.sendGroupMsg(groupMsg,random_say.say());
                }
            }


        }
    }



    @OnGroup
    @Filter(value="nana骰子",trim=true,matchType = MatchType.CONTAINS)
    public void tz(GroupMsg groupMsg, Sender sender) {
        String accountCode = groupMsg.getAccountInfo().getAccountCode();

        sender.sendGroupMsg(groupMsg, groupMsg.getAccountInfo().getAccountNickname()+"您的骰子是");
        sender.sendGroupMsg(groupMsg,"[CAT:at,code="+accountCode+"]"+"[CAT:dice,random=true]");
        }

    //==========================================================================




    @Async
    @OnGroup
    @Filter(value="nana关闭定时模块",trim=true,matchType = MatchType.CONTAINS)
    public void ds(GroupMsg groupMsg, Sender sender) {
        AccountInfo accountInfo = groupMsg.getAccountInfo();
        String accountCode = accountInfo.getAccountCode();  //获取发送人的QQ号
        if (Integer.parseInt(adminService.get_Admin_permission(accountCode).getPermission()) <= 2) {
            synchronized (this) {
                ds_flag = false;
                sender.sendGroupMsg(groupMsg,"定时模块已经关闭");
            }

        }
    }
    @Async
    @OnGroup
    @Filter(value="nana启动定时模块",trim=true,matchType = MatchType.CONTAINS)
    public void dso(GroupMsg groupMsg, Sender sender) {
        AccountInfo accountInfo = groupMsg.getAccountInfo();
        String accountCode = accountInfo.getAccountCode();  //获取发送人的QQ号
        if (Integer.parseInt(adminService.get_Admin_permission(accountCode).getPermission()) <= 2) {
            synchronized (this) {
                ds_flag = true;
                sender.sendGroupMsg(groupMsg,"定时模块已经开启");
            }

        }
    }




    @Async
    @OnGroup
    @Filter(value="nana关闭回复模块",trim=true,matchType = MatchType.CONTAINS)
    public void stopStudy(GroupMsg groupMsg, Sender sender) {
        AccountInfo accountInfo = groupMsg.getAccountInfo();
        String accountCode = accountInfo.getAccountCode();  //获取发送人的QQ号
        if (Integer.parseInt(adminService.get_Admin_permission(accountCode).getPermission()) == 0) {
            synchronized (this) {
                send_flag = false;
                sender.sendGroupMsg(groupMsg,"回复模块已经关闭");
            }

        }
    }
    @Async
    @OnGroup
    @Filter(value="nana启动回复模块",trim=true,matchType = MatchType.CONTAINS)
    public void startStudy(GroupMsg groupMsg, Sender sender) {
        AccountInfo accountInfo = groupMsg.getAccountInfo();
        String accountCode = accountInfo.getAccountCode();  //获取发送人的QQ号
        if (Integer.parseInt(adminService.get_Admin_permission(accountCode).getPermission()) == 0) {
            synchronized (this) {
                send_flag = true;
                sender.sendGroupMsg(groupMsg,"回复模块已经开启");
            }

        }
    }
}