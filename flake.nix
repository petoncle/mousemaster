{
  description = "Development environment for Mousemaster - Linux port";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-26.05";
    # nixos-24.11 ships Qt 6.8.x, matching QtJambi 6.8.2.
    # QtJambi does a hard version check and refuses Qt 6.9+.
    nixpkgs-qt68.url = "github:nixos/nixpkgs/nixos-24.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, nixpkgs-qt68, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        pkgs68 = import nixpkgs-qt68 { inherit system; };
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

            # Qt 6.8.x runtime — must match QtJambi 6.8.2 (from nixos-24.11)
            pkgs68.qt6.qtbase

            # System libs that Qt 6 and the bundled QtJambi .so files will dlopen
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
            export LD_LIBRARY_PATH="${pkgs68.qt6.qtbase}/lib:${pkgs.lib.makeLibraryPath (with pkgs; [
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
            export QT_QPA_PLATFORM_PLUGIN_PATH="${pkgs68.qt6.qtbase}/lib/qt-6/plugins/platforms"
            echo "Mousemaster dev environment ready"
            echo "Java: $(java -version 2>&1 | head -n1)"
            echo "Maven: $(mvn -version 2>&1 | head -n1)"
          '';
        };
      });
}
