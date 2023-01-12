import java.util.ArrayList;

public class ChatAppLayer implements BaseLayer {
    public int nUpperLayerCount = 0;
    public String pLayerName = null;
    public BaseLayer p_UnderLayer = null;
    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    _CHAT_APP m_sHeader;

    private byte[] fragBytes;
    private int fragCount = 0;
    private ArrayList<Boolean> ackChk = new ArrayList<Boolean>();

    private class _CHAT_APP {   //헤더 객체 클래스 새로 생성
        byte[] capp_totlen;      //헤더의 전체 길이 
        byte capp_type;         //단편화 확인하는 type정보 저장하는 변수 
        byte capp_unused;      //사용하지 않는 unused 바이트
        byte[] capp_data;      //chat app data

        public _CHAT_APP() {
            this.capp_totlen = new byte[2];   //2byte
            this.capp_type = 0x00;         //1byte
            this.capp_unused = 0x00;      //1byte //ChatAppHeader 총 4byte
            this.capp_data = null;         //Chat App data
        }
    }

    public ChatAppLayer(String pName) {
        // super(pName);
        // TODO Auto-generated constructor stub
        pLayerName = pName;
        ResetHeader();
        ackChk.add(true);
    }

    private void ResetHeader() {   //Ethernet레이어로 보내는 과정에 필요
        m_sHeader = new _CHAT_APP();//헤더를 세팅한다.
    }

    //ChatApp레이어에서 Send할 때 헤더를 붙이기 위해 사용하는 함수 
    private byte[] ObjToByte(_CHAT_APP Header, byte[] input, int length) {
        byte[] buf = new byte[length + 4];

        buf[0] = Header.capp_totlen[0];   // 헤더의 길이(=크기) totlen 저장 
        buf[1] = Header.capp_totlen[1];   // 헤더의 길이(=크기) totlen 저장
        buf[2] = Header.capp_type;      // type 값 저장 //즉, 단편화 정보
        buf[3] = Header.capp_unused;   // 사용되지 않는 바이트 unused 저장

        if (length >= 0) System.arraycopy(input, 0, buf, 4, length);
        //System.arraycopy함수는 배열을 복사할 때 사용.
        
        return buf;
    }

    
    public byte[] RemoveCappHeader(byte[] input, int length) {
        byte[] cpyInput = new byte[length - 4];
        System.arraycopy(input, 4, cpyInput, 0, length - 4);
        input = cpyInput;
        return input;
    }

    //일정시간씩 기다리며 ACK가 들어오는지 확인하는 메소드
    private void waitACK() {   
        while (ackChk.size() <= 0) { // ACK가 없으면 대기상태
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        ackChk.remove(0);
    }
  
    //단편화 메소드 fragSend 
    //반복문으로 각 frag마다 ACK를 받은 후 잘라서 전송한다.
    private void fragSend(byte[] input, int length) {
        byte[] bytes = new byte[1456]; //1456 크기만큼 바이트 할당 
        int i = 0;
        m_sHeader.capp_totlen = intToByte2(length);   //데이터 총길이 헤더에 추가 
        m_sHeader.capp_type = (byte) (0x01);   //첫번 째 전송 0x01로 설정

        // 첫번째 전송
        System.arraycopy(input, 0, bytes, 0, 1456);
        bytes = ObjToByte(m_sHeader, bytes, 1456);
        this.GetUnderLayer().Send(bytes, bytes.length);   //하위 계층으로 전송

        // 중간 단편화 데이터 전송
        int maxLen = length / 1456;          // 전체 길이를 측정
        m_sHeader.capp_type = (byte) (0x02);   //중간 단편화 데이터는 0X02로 설정
        m_sHeader.capp_totlen = intToByte2(1456);   //데이터 총길이 헤더에 추가 
        
        for(i = 1; i < maxLen; i ++) {   //반복문으로 각 frag마다 ACK를 수행
           waitACK();
           
           if (i + 1 < maxLen && length % 1456 == 0)   // 마지막일 데이터일 경우
              m_sHeader.capp_type = (byte) (0x03);   //0x03으로 지정
           System.arraycopy(input, 1456 * i, bytes, 0, 1456);
           bytes = ObjToByte(m_sHeader, bytes, 1456);
           this.GetUnderLayer().Send(bytes, bytes.length);   //하위계층으로 전송
        }
        
        if (length % 1456 != 0) {
           waitACK();
            m_sHeader.capp_type = (byte) (0x03);
            m_sHeader.capp_totlen = intToByte2(length % 1456);
            bytes = new byte[length % 1456];
            System.arraycopy(input, length - (length % 1456), bytes, 0, length % 1456);
            bytes = ObjToByte(m_sHeader, bytes, bytes.length);
            this.GetUnderLayer().Send(bytes, bytes.length);
        }
    }
 
    //send 호출 시 ObjToByte를 통해 underLayer에 보낼 값에 헤더를 붙일 수 있다. 
    public boolean Send(byte[] input, int length) {
        byte[] bytes;
        m_sHeader.capp_totlen = intToByte2(length);   
        m_sHeader.capp_type = (byte) (0x00);
        
        waitACK();
        if (length > 1456) { // 1456바이트 이상인 경우 
           fragSend(input, length);   //단편화 fragSend함수 호출
        }
        else {  //단편화 필요 없는 1456 미만의 경우 
           bytes = ObjToByte(m_sHeader, input, length);   //헤더를 추가하고
          this.GetUnderLayer().Send(bytes, bytes.length);   //하위계층으로 전송
        }
        return true;
    }
 
    //하위 계층에서 전송 받은 데이터의 type이 0인 경우 헤더만 제거 후 상위 계층으로 전송
    //나머지 type인 경우, 단편화된 데이터이므로 차례로 병합 후 type3이 될 시 상위계층으로 보낸다. 
    //즉, type 1 =첫 단편화 데이터 type 2 = 중간 데이터 type3 =마지막 데이터
    public synchronized boolean Receive(byte[] input) {
        byte[] data, tempBytes;
        int tempType = 0;

        if (input == null) { // null인 경우 ack
           ackChk.add(true);
           return true;
        }
        
        tempType |= (byte) (input[2] & 0xFF); // 헤더의 타입
        
        if(tempType == 0) { // 단편화 없이 그대로 들어온 데이터
           data = RemoveCappHeader(input, input.length);//데이터 헤더제거
           this.GetUpperLayer(0).Receive(data);//상위 계층으로 전송
        }
        else{
           if (tempType == 1) { //첫 단편화된 데이터 // 단편화 될 데이터의 총 크기
              int size = byte2ToInt(input[0], input[1]);
              fragBytes = new byte[byte2ToInt(input[0], input[1])]; // 크기(size)만큼의 배열을 만들어주고
              fragCount = 1;
              tempBytes = RemoveCappHeader(input, input.length); // 데이터의 헤더를 제거해서
              System.arraycopy(tempBytes, 0, fragBytes, 0, 1456); // fragBytes에 넣어준다
           }
           else { // 중간 단편화 데이터들
              tempBytes = RemoveCappHeader(input, input.length); //데이터의 헤더 제거해서 tempBytes에 할당 
              System.arraycopy(tempBytes, 0, fragBytes, (fragCount++) * 1456, byte2ToInt(input[0], input[1]));//fragBytes에 넣어준다.
              if (tempType == 3) {    // 마지막 단편화 데이터
                 this.GetUpperLayer(0).Receive(fragBytes);   //상위계층으로 보냄
              }
           }
        }
        this.GetUnderLayer().Send(null, 0); // ack 송신 //receive함수 실행시 매번 실행된다.
        return true;
    }
    
    private byte[] intToByte2(int value) { 
        byte[] temp = new byte[2];
        temp[0] |= (byte) ((value & 0xFF00) >> 8);
        temp[1] |= (byte) (value & 0xFF);

        return temp;
    }

    private int byte2ToInt(byte value1, byte value2) {
        return (int)((value1 << 8) | (value2));
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