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
}