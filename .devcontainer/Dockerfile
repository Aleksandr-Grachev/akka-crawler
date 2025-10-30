FROM debian:bookworm-slim

ARG USERNAME=developer
ENV DEBIAN_FRONTEND=noninteractive

# Базовые пакеты
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl zip unzip git sudo bash ca-certificates gnupg

# Install locales package
RUN apt-get install -y --no-install-recommends  -y locales

# Uncomment en_US.UTF-8 for inclusion in generation
RUN sed -i 's/^# *\(en_US.UTF-8\)/\1/' /etc/locale.gen
RUN locale-gen && rm -rf /var/lib/apt/lists/*

ENV LC_ALL en_US.UTF-8

# Создаём пользователя
RUN useradd -s /bin/bash --uid 1000 --gid 100 --create-home $USERNAME && \
    echo "$USERNAME ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME

USER $USERNAME
WORKDIR /workspace

# Устанавливаем SDKMAN
RUN curl -s "https://get.sdkman.io" | bash
ENV SDKMAN_DIR="/home/$USERNAME/.sdkman"
ENV PATH="$SDKMAN_DIR/bin:$PATH"

# Устанавливаем нужные пакеты через sdkman
RUN bash -c "source $SDKMAN_DIR/bin/sdkman-init.sh && \
    sdk install java 17.0.15-tem && \
    sdk install sbt 1.10.10 && \
    sdk install maven 3.9.9 && \
    sdk install scala 2.13.17"

# Установка code-server
RUN curl -fsSL https://code-server.dev/install.sh | sh

# Устанавливаем расширения
# послойно поскольку бывают отказы хоста с extensions
RUN code-server --install-extension scalameta.metals
RUN code-server --install-extension scala-lang.scala
RUN code-server --install-extension redhat.vscode-yaml
#RUN code-server --install-extension ms-azuretools.vscode-containers

EXPOSE 8183

ENTRYPOINT ["code-server", "--bind-addr", "0.0.0.0:8183", "--auth", "none"]
CMD ["."]
