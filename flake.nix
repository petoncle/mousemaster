{
  description = "Development environment for Mousemaster - Linux port";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-26.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            # Java development
            jdk21
            maven

            # X11 libs loaded by JNA at runtime
            libx11
            libxrandr
            libxtst    # XTest extension - for key/mouse injection
            libxi
            libxext
            libxrender
            libxfixes

            # System libs that the bundled Qt 6.8.2 .so files will dlopen
            libxcb
            xcbutil
            xcbutilimage
            xcbutilkeysyms
            xcbutilrenderutil
            xcbutilwm
            libxkbcommon
            fontconfig
            freetype
            mesa   # provides libGL / libEGL
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk21}"
            export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath (with pkgs; [
              libx11
              libxrandr
              libxtst
              libxi
              libxext
              libxrender
              libxfixes
              libxcb
              xcbutil
              xcbutilimage
              xcbutilkeysyms
              xcbutilrenderutil
              xcbutilwm
              libxkbcommon
              fontconfig
              freetype
              mesa
            ])}:$LD_LIBRARY_PATH"
            echo "Mousemaster dev environment ready"
            echo "Java: $(java -version 2>&1 | head -n1)"
            echo "Maven: $(mvn -version 2>&1 | head -n1)"
          '';
        };
      });
}
