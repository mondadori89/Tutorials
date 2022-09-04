# Tutorial Devops - AWS / Jenkins / Docker / Gitlab

- aquivos:
    - pipeline.groovy
    - pipeline_completo.groovy
    - comandos.txt
    - dcw-app.zip

</br></br>

>## Setup inicial

- Criar repositório no Gitlab, deixar como público
    - Clonar repositório para o PC
    - Colocar aplicação na pasta do repositório
    - Commit

- Criar Conta no DockerHub

- Criar conta na AWS
</br></br>

>## Setup Jenkins

- Criar VM na AWS para o Jenkins 
    - Nome: Jenkins
    - Par de Chaves: Prosseguir sem par de chaves
    - Criar Grupo de Segurança:
        - Editar, Nome: Tudo liberado.
        - Regra do grupo, Tipo: Todo o tráfego.
    - Detalhes avançados, Dados do usuário:
        ```
        #!/bin/bash
        sudo yum update -y
        sudo amazon-linux-extras install epel -y
        sudo yum install daemonize -y
        sudo wget -O /etc/yum.repos.d/jenkins.repo \
            https://pkg.jenkins.io/redhat-stable/jenkins.repo
        sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io.key
        sudo yum upgrade
        sudo amazon-linux-extras install java-openjdk11 -y
        sudo yum install jenkins -y
        sudo systemctl daemon-reload
        sudo systemctl start jenkins
        sudo cat /var/lib/jenkins/secrets/initialAdminPassword
        ```

**Output: Instância Jenkins rodando**
</br></br>

- Ativar Jenkins
    - Conectar na VM Jenkins e rodar: (Se tiver que interromper a máquina, rodar esses comandos novamente)
        ``` 
        sudo yum update -y                           # Atualiza os softwares disponiveis para o sistema
        sudo yum install git -y                      # Instala o git
        sudo amazon-linux-extras install docker -y   # Instala o docker
        sudo systemctl start docker                  # Inicia o docker
        sudo usermod -aG docker jenkins              # Da permissões ao Jenkins para rodar comandos Docker
        sudo systemctl restart jenkins               # Reinicia o Jenkins para aplicar as permissões anteriormente adicionadas
        ```
    - Copiar código gerado na VM
    - Colocar o IP_externo:8080
    - Configurar novo Jenkins:
        - Colar código gerado na VM no “senha do administrador”
        - Selecionar “Select plug-ins...”
        - Selecionar o Gitlab
        - Criar usuário (mondadori89)

**Output: Jenkins ready**
</br></br>

>## Setup Server

- Criar nova Instância
    - Nome: app-server
    - OS: Ubuntu, Ubuntu server 20.04
    - Par de chaves (opcional)
        - Criar novo par de chaves, nome: dcw, criar par de chaves
        - Salvar par de chaves (arquivo) .pem no PC
    - Grupo de segurança: Tudo liberado
    - Detalhes avançados, Dados do usuário:
        ```
        #!/bin/bash
        sudo apt-get update
        sudo apt-get upgrade -y
        sudo apt-get install docker.io git -y
        sudo usermod -aG docker ubuntu
        sudo reboot
        ```

- Exemplo de rodar um App no server a partir de um container/imagem
    - Conectar na instância app-server e rodar:
        ```
        docker pull ngix                                    # instalar ngix
        docker run --name app1 -d -p 8080:80 nginx          # rodar imagem
        docker ps                                           # para ver as imagens rodando
        docker rm -f app1                                   # para remover a imagem rodando
        ```
    - Acessar IP:8080 para ver o app live 

- Levar aplicação para o app-server
    - Conectar na instância app-server e rodar:
        ```
        git clone <url do repositório>                     
            colocar usuário e senha do gitlab
        ```
    - No app tem um "dockerfike" com os comandos:
        ```
        FROM node:14
        WORKDIR /usr/src/app
        COPY . .                    # copiando a pasta atual "." para dentro do container               
        RUN npm install
        EXPOSE 3000
        CMD [ "npm", "start" ]
        ```
</br></br>

>## Montagem manual do app na VM

- conectar na VM, cd pasta do app e rodar:
    ```
    docker build -t app-dcw5 .                                  # vai rodar o dockerfile criando uma imagem com nome app-dcw5
    docker images                                               # para ver imagens instaladas na máquina
    docker run -itd -p 80:3000 app-dcw5                         # Inicializa o novo container com a versão especificada: docker run -itd -p 80:3000 REPOSITORY_NAME/dcw-app:TAG 
    docker ps                                                   # para ver as imagens rodando
    ```
- IP no browser para ver a aplicação rodando
</br></br>

>## Enviar imagem para o Docker Hub

- No Docker Hub criar um repositório público (app-dcw5)
- conectar na VM e rodar:
    ```
    docker login                        
        informar usuário e senha
    docker tag app-dcw5 mondadori89/app-dcw5                    # app-dcw5 = imagem-local  |  mondadori89/app-dcw5 = usuário-repositório no docker hub
    docker push mondadori89/app-dcw5                            # enviando imagem para o docker hub 
    docker rm -f id_do_container                                # para parar o container que estava rodando antes
    docker run -itd -p 80:3000 mondadori89/app-dcw5             # rodar o novo container que está conectado no dockerhub
    ```
</br></br>

>## Automatizando o Docker build

- Preparar o pipeline.groovy: Alterar linha 5 e 13  
- Entrar no Jenkins
    - Setup do cofre de senhas:
        - Gerenciar jenkins
        - Segurança, menage credentials, clicar no Scope Jenkins, clicar no Global credentials
            - Add credentials: kind: username with password
                - usuário e senha do dockerhub
                - ID: dockerhub_id   // é o que está no pipeline.groovy linha 6
    - Gerenciar jenkins, gerenciar extensões, disponíveis
        - Instalar "Docker Pipeline"
        - Clicar em baixar agora e instalar após reinício
        - checar caixa "reinicie aquando a instalação estiver completa"
    - Novo Job: Nome: Bild Aplication
        - Selecionar Pipeline
        - No final, em Pipeline script colar o conteúdo do pipeline.groovy (sem comentários)
    - Run no Job Build application

- Deploy da aplicação manual: 
    - Conectar na VM app-server e rodar:
        ```
        docker ps                                                       # ver o que está rodando
        docker rm -f id_container                                       # remover o container que está rodando
        docker images                                                   
        docker rmi mondadori89/app-dcw5:develop                         # remover imagem anterior caso exista
        docker run -itd -p 80:3000 mondadori89/dcw-app:develop          # especificando a versão (:develop que foi indicado no pipeline.groovy)
        ```
</br></br>

>## Automatizando o Deploy com AWS CodeDeploy

- Instalar pré-requisitos no servidor
    - Conectar na VM app-server e rodar:
        ```
        sudo apt install ruby-full -y                                                       # Instala o Ruby
        wget https://aws-codedeploy-us-east-1.s3.us-east-1.amazonaws.com/latest/install     # Faz o download do agente do codedeploy
        chmod +x ./install                                                                  # Da permissão de execução para o arquivo
        sudo ./install auto > /tmp/logfile                                                  # executa a instalação do agente
        sudo service codedeploy-agent status                                                # Verifica se o agente foi iniciado corretamente
        sudo reboot                                                                         # Reinicia a maquina para garantir que o agente entre em execução
        ```

- Criar roles no IAM 
    - Criar função(Role) CodeDeployRole:
        - No fim, em "escolha um serviço..." colocar "codedeploy"
        - Selecionar "CodeDeploy", Next, Next
        - Nome da função: CodeDeployRole, Criar função
    - Criar função(Role) EC2toS3RO:
        - Em casos de uso, selecionar "EC2", Next
        - Em Adicionar pemissões filtrar por: s3
        - Adicionar: "AmazonS3ReadOnlyAccess", Next
        - Nome da função: EC2toS3RO, Criar função

- Adicionar Role na VM app-server
    - Na EC2, clicar com botão direito na app-server
        - Segurança, Modificar função do IAM
    - Adicionar EC2toS3RO role e atualizar
    - Reiniciar a instância (caso necessário)
    
- Criar Bucket na S3 e adicionar scripts
    - Nome: mondadori89-dcw-2022 (ou qualquer outro nome)
    - Adicionar no bucket o arquivo dcw-app.zip com:
        - appspec.yml
        - pasta Scripts com start e stop-container.sh

- Aplicativo no CodeDeploy:
    - Ir em Aplicativos, Criar Aplicativo
        - Nome: dcw-app, Plataforma: EC2/On-permissões, criar Aplicativo
    - Em Grupos de implantação, Criar grupo de implantação
        - Nome: dcw-app-group
        - Função: CodeDeployRole
        - Configuração do ambiente: Instâncias do Amazon EC2
            - Chave: Name - Valor: app-server (nome da instância da EC2)
        - Instalar agente do Aws CodeDeploy: "Nunca"
        - Load balancer: desabilitar
        - Botão Criar grupo de implantação
    - Entrar no grupo de implantação: dcw-app-group
        - Criar implantação
            - Local de revisão: s3://bucket-name/folder/object.zip - Criar implantação

- Deploy com CodeDeploy:
    - Entrar no aplicativo, grupo de implantação
        - Repetir implantação


>## Automatizando CodeDeploy no Jenkins

- Instalar plugin AWS CodeDeploy no Jenkins
    - No Jenkins, Gerenciar extensões, disponíveis
    - Procurar por: AWS CodeDeploy
        - Clicar em baixar agora e instalar após reinício
        - checar caixa "reinicie aquando a instalação estiver completa"

- Criar novo usuário IAM  
    - Na AWS, IAM, Usuários, Adicionar usuário
        - Nome: Jenkins
        - Selecionar: Chave de acesso: acesso programático, Next
        - Ir em Anexar políticas:
            - Selecionar AdministradorAccess (por ser mais prático porém menos seguro...)
            - Copiar ID ad chave de acesso e a Chave de acesso secreta para um arquivo seguro no PC.

- Atualizar pippeline_completo.groovy
    - Atualizar linhas com o comment Alterar
    - ATENÇÃO para não compartilhar com ninguém as Chaves de Acesso

- Configurar Job do Jenkins
    - No job já criado, ir em Configurar
    - Colar conteúdo do pippeline_completo.groovy no espaço script

**Output: Ao clicar Run Job, Deploy completo será executado.**
</br></br>

>## Automatizando Deploy completo com Push no Gitlab

- Ter o plugin do GitLab instalado no Jenkins

- Gerar Gitlab API token
    - No Gitlab, ir no perfil, preferências
    - Na esquerda, Access Token:
        - Token Name: Jenkins
        - Select scopes: selecionar todos 
        - Create personal Access Token
        - Copiar o token gerado

- Conecção Jenkins-Gitlab
    - No Jenkins, ir em Gerenciar Jenkins, Configurar o sistema
        - Em Jenkins location, verificar se a URL do Jenkins está correto
        - Em Gitlab, selecionar o Enable
            - Connection name: GitLab
            - Gitlab host URL: https://gitlab.com/
            - API token, clicar em + Add, Jenkins
                - Kind: Gitlab/API token
                - API token, colar o token gerado no Gitlab
                - ID: GITLAB-NOVO (ou outro nome como preferir)
                - Add
                - Na tela anterior selecionar o token criado
            - Test connection, Output esperado: Success

- Configurar Job
    - Entrar no Job, Configurar
    - Em Build Triggers
        - Selecionar "Build when a change is pushed to Gitlab"
            - Copiar URL gerada abaixo do botão
            - Ir no botão "Avançado"
            - No final: Generate Secret Token
            - Copiar Secret Token
        - Salvar Job

- Colocar Token do Job do Jenkins no Gitlab
    - No Gitlab, repositório, Settings, Webhooks
        - URL: colocar o URL indicada no item Build Triggers do Job do Jenkins
        - Secret Token: Colar o Secret token gerado no Job do Jenkins 
        - Disable SSL Verification (pois não estamos usando https)
        - Add webhook
        - Test, Push event, Output esperado: Novo job deve começar no Jenkins

**Output: Ao dar Push na branch do Gitlab, Deploy completo será executado**
</br></br>
    

