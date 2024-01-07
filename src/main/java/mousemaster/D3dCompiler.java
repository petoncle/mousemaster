package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinDef.UINT;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.util.List;

public interface D3dCompiler extends StdCallLibrary {

    // This loads the specific version of d3dcompiler that is supposed to be commonly available.
    D3dCompiler INSTANCE = Native.load("d3dcompiler_47", D3dCompiler.class);

    WinNT.HRESULT D3DCompile(
        Pointer pSrcData, 
        BaseTSD.SIZE_T SrcDataSize,
        String pSourceName,
        D3D_SHADER_MACRO.ByReference pDefines,
        Pointer pInclude, // ID3DInclude is typically used as a callback interface, use Pointer for simplicity
        String pEntrypoint,
        String pTarget,
        UINT Flags1,
        UINT Flags2,
        PointerByReference ppCode, // ID3DBlob** 
        PointerByReference ppErrorMsgs // ID3DBlob**
    );

    class D3D_SHADER_MACRO extends Structure {
        public String Name;
        public String Definition;

        protected List<String> getFieldOrder() {
            return List.of("Name", "Definition");
        }

        public static class ByReference extends D3D_SHADER_MACRO implements Structure.ByReference {}
    }

}
