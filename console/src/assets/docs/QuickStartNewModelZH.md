Starwhale 模型是一种机器学习模型的标准包格式，包含模型文件、推理代码、配置文件等等。

**第一步：使用 venv 安装 Starwhale Client**

```
python3 -m venv ~/.cache/venv/starwhale
source ~/.cache/venv/starwhale/bin/activate
python3 -m pip install starwhale

swcli --version

sudo rm -rf /usr/local/bin/swcli
sudo ln -s "$(which swcli)" /usr/local/bin/
```

**第二步：登录云实例**

```
swcli instance login --username <您的用户名> --password <您的密码> --alias swcloud https://cloud.starwhale.cn
```

**第三步：构建新模型**

```
swcli model build . --model-yaml /path/to/model.yaml
```

**第四步：在 UI 页面的模型列表页查看**
