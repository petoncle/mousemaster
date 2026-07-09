{
  description = "Development environment for Mousemaster - Linux port";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-26.05";
    nixpkgs-qt682.url = "github:nixos/nixpkgs/86c0981230fd186dcefe317db93963c0bcdd1810";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, nixpkgs-qt682, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        pkgs-qt682 = import nixpkgs-qt682 { inherit system; };
      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            # Java development
            jdk21
            maven

            # X11 libraries for JNA bindings
            xorg.libX11
            xorg.libXrandr
            xorg.libXtst
            xorg.libxcb

            # Qt 6.8.2 libraries - pinned to nixpkgs commit before 6.8.3 to match QtJambi 6.8.2
            pkgs-qt682.qt6.full
            pkgs-qt682.qt6.qtbase

            # Additional X11 dependencies
            xorg.libXi
            xorg.libXext
            xorg.libXrender
            xorg.libXfixes

            # Build tools
            pkg-config
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk21}"
            export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath [
              pkgs.xorg.libX11
              pkgs.xorg.libXrandr
              pkgs.xorg.libXtst
              pkgs.xorg.libxcb
              pkgs-qt682.qt6.qtbase
              pkgs.xorg.libXi
              pkgs.xorg.libXext
            ]}:$LD_LIBRARY_PATH"
            echo "Mousemaster development environment loaded"
            echo "Java: $(java -version 2>&1 | head -n1)"
            echo "Maven: $(mvn -version | head -n1)"
          '';
        };
      });
}
