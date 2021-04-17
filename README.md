
# Connectivity-RE

```bash
export CONNECTIVITY_RE_HOME=/full/path/to/bio_electra/repository
```

### GPU requirements
You need Tensorflow 1.15 and CUDA 10.0 for GPU.


### Setup virtual environment

Ensure you have virtual environment support (e.g. for Ubuntu)
```
sudo apt-get install python3-venv
```

```
python3 -m venv --system-site-packages $CONNECTIVITY_RE_HOME/venv
```

```
source $CONNECTIVITY_RE_HOME/venv/bin/activate

pip install --upgrade pip
pip install tensorflow-gpu==1.15
pip install sklearn
pip install hyperopt

```


## Download Bio-ELECTRA models


The pre-trained Bio-ELECTRA and Bio-ELECTRA++ small ELECTRA models are available at Zenodo. [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.3971235.svg)](https://doi.org/10.5281/zenodo.3971235)



## Datasets
All of the datasets are available at `$CONNECTIVITY_RE_HOME/electra/data/finetuning_data`. 


