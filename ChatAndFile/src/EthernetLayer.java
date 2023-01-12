

import java.util.ArrayList;

//송신자 주소, 목적지 주소, MAC주소 관리
public class EthernetLayer implements BaseLayer {

   public int nUpperLayerCount = 0;
   public String pLayerName = null;
   public BaseLayer p_UnderLayer = null;
   public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
   _ETHERNET_Frame m_sHeader;
   
   public EthernetLayer(String pName) {
      // super(pName);
      // TODO Auto-generated constructor stub
      pLayerName = pName;
      ResetHeader();
   }
   
   public void ResetHeader() {   //헤더 설정
      m_sHeader = new _ETHERNET_Frame();
   }
   
    private class _ETHERNET_ADDR {   //6byte 크기의 16진수형의 주소를 받는 것을 담도록
        private byte[] addr = new byte[6];

        public _ETHERNET_ADDR() {
            this.addr[0] = (byte) 0x00;
            this.addr[1] = (byte) 0x00;
            this.addr[2] = (byte) 0x00;
            this.addr[3] = (byte) 0x00;
            this.addr[4] = (byte) 0x00;
            this.addr[5] = (byte) 0x00;
        }
    }
    
    //Ethernet Header: dst_addr, src_addr, type
    //Ethernet Data : data
    private class _ETHERNET_Frame { 
        _ETHERNET_ADDR enet_dstaddr;   //목적지 주소 //물리적 주소값
        _ETHERNET_ADDR enet_srcaddr;   //소스 주소  //물리적 주소값
        byte[] enet_type;            //type: frame이 ack인지 data인지 구별해주도록 함
        byte[] enet_data;

        public _ETHERNET_Frame() {
            this.enet_dstaddr = new _ETHERNET_ADDR();   //6byte크기 
            this.enet_srcaddr = new _ETHERNET_ADDR();   //6byte크기
            this.enet_type = new byte[2];            //2byte크기  // Ethernet Header 총 14byte 
            this.enet_data = null;                  //data
        }
    }
    
    public byte[] ObjToByte(_ETHERNET_Frame Header, byte[] input, int length) {
      byte[] buf = new byte[length + 14];
      for(int i = 0; i < 6; i++) {
         buf[i] = Header.enet_dstaddr.addr[i];      //목적지 주소 설정 할당
         buf[i+6] = Header.enet_srcaddr.addr[i];      //소스 주소 설정 할당
      }         
      buf[12] = Header.enet_type[0];               //type 할당
      buf[13] = Header.enet_type[1];               //type 할당
      for (int i = 0; i < length; i++)            //data할당
         buf[14 + i] = input[i];

      return buf;
   }

   
    //상위 계층으로 부터 데이터를 전달받으면 그 데이터를 프레임의 데이터에 저장
    //수신될 Ethernet 주소와 자신의 Ethernet주소를 헤더에 저장
    //상위 계층의 종류에 따라서 헤더에 상위 프로토콜 타입을 저장 후 물리적 계층으로 ethernet frame 전달
    
    //채팅 전송 메소드
   public boolean Send(byte[] input, int length) {      //input: 보낼 데이터
      if (input == null && length == 0)             // ack //데이터가 null==ack
         m_sHeader.enet_type = intToByte2(0x2082);      //ack type : 0x2082
      else if (isBroadcast(m_sHeader.enet_dstaddr.addr)) // broadcast경우 
         m_sHeader.enet_type = intToByte2(0xff);         //broadcast type : 0xff
      else // 일반 data경우
         m_sHeader.enet_type = intToByte2(0x2080);   //일반 채팅 type : 0x2081
      
      byte[] bytes = ObjToByte(m_sHeader, input, length);   //ObjToByte함수를 호출하여 헤더 추가 
      this.GetUnderLayer().Send(bytes, length + 14);  //하위 계층으로 보낸다. 
      return true;
   }
    
   //파일 전송 메소드
   public boolean fileSend(byte[] input, int length) {
      if (input == null && length == 0)             // ack //데이터가 null==ack
            m_sHeader.enet_type = intToByte2(0x2092);      //ack type : 0x2091
      else if (isBroadcast(m_sHeader.enet_dstaddr.addr)) // broadcast경우 
            m_sHeader.enet_type = intToByte2(0xff);         //broadcast type : 0xff
      else // 일반 data경우
            m_sHeader.enet_type = intToByte2(0x2090);   //일반 채팅 type : 0x2090
         
         byte[] bytes = ObjToByte(m_sHeader, input, length);   //ObjToByte함수를 호출하여 헤더 추가 
         this.GetUnderLayer().Send(bytes, length + 14);  //하위 계층으로 보낸다. 
         return true;
   }

   //헤더 제거 후 반환하는 메소드
   public byte[] RemoveEthernetHeader(byte[] input, int length) {   
      byte[] cpyInput = new byte[length - 14];
      System.arraycopy(input, 14, cpyInput, 0, length - 14);
      input = cpyInput;
      return input;
   }
   
   //하위 계층으로부터 프레임을 받으면 상위로 보내야 하는지, 폐기해야하는지 결정
   //브로드캐스트이거나 목적지 주소가 자신인 경우만 상위 계층으로 전송
   public synchronized boolean Receive(byte[] input) {
         byte[] data;
         int temp_type = byte2ToInt(input[12], input[13]); 
         //chat
         if(temp_type == (byte2ToInt((byte) 0x20, (byte) 0x82))) { //ack
            this.GetUpperLayer(0).Receive(null); // 상위 계층인 chatapplayer에 알린다
         }else if(isBroadcast(input) ||   //브로드캐스트인지 확인
               (temp_type == byte2ToInt((byte) 0x20, (byte) 0x80)   //chat 데이터이고
                  && !isMyPacket(input) && chkAddr(input))) { //나 자신의 주소인지 확인
            data = RemoveEthernetHeader(input, input.length); //헤더제거
            this.GetUpperLayer(0).Receive(data); //상위 계층인 chatapplayer로 receive
            return true;
         }
         //file
         else if(temp_type == byte2ToInt((byte) 0x20, (byte) 0x92)) { //ack
            this.GetUpperLayer(1).Receive(null); // 상위 계층인 fileapplayer에 알린다
         }else if(isBroadcast(input) ||   //브로드캐스트인지 확인
               (temp_type == byte2ToInt((byte) 0x20, (byte) 0x90)   //file 데이터이고
                  && !isMyPacket(input) && chkAddr(input))) { //나 자신의 주소인지 확인
            data = RemoveEthernetHeader(input, input.length); //헤더 제거
            this.GetUpperLayer(1).Receive(data); //상위 계층인 fileapplayer로 receive
            return true;
         }
         return false;
      }

    private byte[] intToByte2(int value) {   //정수를 바이트로 변환
        byte[] temp = new byte[2];
        temp[0] |= (byte) ((value & 0xFF00) >> 8);
        temp[1] |= (byte) (value & 0xFF);

        return temp;
    }

    private int byte2ToInt(byte value1, byte value2) {
        return (int)((value1 & 0xff<<8) | (value2 & 0xff));
    }
   
   private boolean isBroadcast(byte[] bytes) {   //주소가 broadcast인지 판별 (type 확인)
      for(int i = 0; i< 6; i++)
         if (bytes[i] != (byte) 0xff)
            return false;
      return (bytes[12] == (byte) 0xff && bytes[13] == (byte) 0xff);
   }

   private boolean isMyPacket(byte[] input){ //자신의 패킷인지 확인
      for(int i = 0; i < 6; i++)
         if(m_sHeader.enet_srcaddr.addr[i] != input[6 + i])
            return false;
      return true;
   }

   private boolean chkAddr(byte[] input) { //주소 확인
      byte[] temp = m_sHeader.enet_srcaddr.addr;
      for(int i = 0; i< 6; i++)
         if(m_sHeader.enet_srcaddr.addr[i] != input[i])
            return false;
      return true;
   }
   
   public void SetEnetSrcAddress(byte[] srcAddress) {
      // TODO Auto-generated method stub
      m_sHeader.enet_srcaddr.addr = srcAddress;
   }

   public void SetEnetDstAddress(byte[] dstAddress) {
      // TODO Auto-generated method stub
      m_sHeader.enet_dstaddr.addr = dstAddress;
   }
    
   @Override
   public String GetLayerName() {
      // TODO Auto-generated method stub
      return pLayerName;
   }

   @Override
   public BaseLayer GetUnderLayer() {
      // TODO Auto-generated method stub
      if (p_UnderLayer == null)
         return null;
      return p_UnderLayer;
   }

   @Override
   public BaseLayer GetUpperLayer(int nindex) {
      // TODO Auto-generated method stub
      if (nindex < 0 || nindex > nUpperLayerCount || nUpperLayerCount < 0)
         return null;
      return p_aUpperLayer.get(nindex);
   }

   @Override
   public void SetUnderLayer(BaseLayer pUnderLayer) {
      // TODO Auto-generated method stub
      if (pUnderLayer == null)
         return;
      this.p_UnderLayer = pUnderLayer;
   }

   @Override
   public void SetUpperLayer(BaseLayer pUpperLayer) {
      // TODO Auto-generated method stub
      if (pUpperLayer == null)
         return;
      this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);
   }

   @Override
   public void SetUpperUnderLayer(BaseLayer pUULayer) {
      this.SetUpperLayer(pUULayer);
      pUULayer.SetUnderLayer(this);
   }
}