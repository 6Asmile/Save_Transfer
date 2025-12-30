package com.jsafe.common;

public interface Command {
    byte REQ_AUTH = 0x10;
    byte RESP_AUTH = 0x11;
    byte REQ_REGISTER = 0x13;
    byte RESP_REGISTER = 0x14;


    byte REQ_CHECK_RESUME = 0x20;
    byte RESP_CHECK_RESUME = 0x21;

    byte REQ_UPLOAD_DATA = 0x30;
    byte RESP_UPLOAD_ACK = 0x31;

    byte REQ_LIST_FILES = 0x40;
    byte RESP_LIST_FILES = 0x41;

    byte REQ_DOWNLOAD = 0x50;       // 客户端请求下载
    byte RESP_DOWNLOAD_START = 0x51; // 服务端告知：准备好了，文件大小是多少
    byte RESP_DOWNLOAD_DATA = 0x52;  // 服务端发送：数据块

    byte REQ_SHARE = 0x60;
    byte RESP_SHARE = 0x61;

    byte REQ_DELETE = 0x70;
    byte RESP_DELETE = 0x71;
    byte REQ_RENAME = 0x72;
    byte RESP_RENAME = 0x73;
}